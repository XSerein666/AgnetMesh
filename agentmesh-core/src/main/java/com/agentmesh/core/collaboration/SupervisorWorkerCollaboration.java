package com.agentmesh.core.collaboration;

import com.agentmesh.core.agent.AgentConfig;
import com.agentmesh.core.infrastructure.AgentMeshMetrics;
import com.agentmesh.core.infrastructure.TraceIdContext;
import com.agentmesh.core.llm.StreamEvent;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Supervisor-Worker 协作模式实现。
 *
 * 执行流程：
 * 1. 注册所有 Agent 角色到 MessageBus
 * 2. 各 Agent 订阅消息总线
 * 3. Supervisor 通过 LLM Function Calling 拆解任务
 * 4. 通过 MessageBus.requestReply 分发子任务给 Worker
 * 5. 收集 WorkerResult，处理超时/失败/降级
 * 6. 汇总结果生成最终回复
 */
@Slf4j
public class SupervisorWorkerCollaboration implements AgentCollaboration {

    private final Duration requestTimeout;
    private final AgentMeshMetrics meshMetrics;
    private final CollaborationMetrics collabMetrics;

    public SupervisorWorkerCollaboration(Duration requestTimeout,
                                          AgentMeshMetrics meshMetrics,
                                          CollaborationMetrics collabMetrics) {
        this.requestTimeout = requestTimeout;
        this.meshMetrics = meshMetrics;
        this.collabMetrics = collabMetrics;
    }

    @Override
    public Flux<StreamEvent> collaborate(List<AgentConfig> agents,
                                          String input,
                                          SharedContext sharedContext,
                                          MessageBus messageBus,
                                          String collaborationId) {
        return Flux.defer(() -> {
            String traceId = TraceIdContext.get();
            log.info("[SupervisorWorker] 协作启动, collaborationId={}, agentCount={}, traceId={}",
                    collaborationId, agents.size(), traceId);
            collabMetrics.recordCollaborationStarted();

            // 1. 分离 Supervisor 和 Worker
            AgentConfig supervisor = findSupervisor(agents);
            List<AgentConfig> workers = findWorkers(agents);

            if (supervisor == null) {
                log.error("[SupervisorWorker] 未找到 Supervisor Agent, collaborationId={}", collaborationId);
                collabMetrics.recordCollaborationFailed();
                return Flux.just(StreamEvent.builder()
                        .type(StreamEvent.Type.ERROR)
                        .content("未找到 Supervisor Agent（role=supervisor）")
                        .build());
            }
            if (workers.isEmpty()) {
                log.error("[SupervisorWorker] 未找到 Worker Agent, collaborationId={}", collaborationId);
                collabMetrics.recordCollaborationFailed();
                return Flux.just(StreamEvent.builder()
                        .type(StreamEvent.Type.ERROR)
                        .content("未找到 Worker Agent（role=worker）")
                        .build());
            }

            log.info("[SupervisorWorker] Supervisor={}, Workers={}, collaborationId={}",
                    supervisor.getAgentId(),
                    workers.stream().map(AgentConfig::getAgentId).collect(Collectors.toList()),
                    collaborationId);

            // 2. 注册角色并订阅
            messageBus.registerAgentRole(supervisor.getAgentId(), "supervisor");
            for (AgentConfig worker : workers) {
                messageBus.registerAgentRole(worker.getAgentId(), "worker");
            }

            // 每个 Agent 订阅消息总线（Flux 需要被消费，否则 Sink 不会创建）
            Map<String, reactor.core.Disposable> subscriptions = new ConcurrentHashMap<>();
            for (AgentConfig agent : agents) {
                subscriptions.put(agent.getAgentId(),
                        messageBus.subscribe(agent.getAgentId())
                                .subscribe(
                                        msg -> log.debug("[SupervisorWorker] Agent {} 收到消息: type={} from={}",
                                                agent.getAgentId(), msg.getType(), msg.getFromAgentId()),
                                        err -> log.warn("[SupervisorWorker] Agent {} 订阅异常: {}",
                                                agent.getAgentId(), err.getMessage())
                                ));
            }

            // 3. Supervisor 拆解任务 → 分发 → 收集 → 汇总
            return decomposeTask(supervisor, input, sharedContext, collaborationId, traceId)
                    .flatMapMany(decomposition -> {
                        if (decomposition.getSubTasks() == null || decomposition.getSubTasks().isEmpty()) {
                            log.warn("[SupervisorWorker] 任务拆解结果为空，进入降级模式, collaborationId={}", collaborationId);
                            return fallbackToSupervisor(supervisor, input, traceId);
                        }

                        // 写入共享上下文
                        sharedContext.put("supervisor/decomposition", decomposition,
                                supervisor.getAgentId(), "supervisor");

                        // 4. 分发子任务给 Worker
                        List<TaskDecompositionResult.SubTask> validSubTasks = decomposition.getSubTasks().stream()
                                .filter(st -> st.getAssignedWorkerId() != null)
                                .collect(Collectors.toList());

                        if (validSubTasks.isEmpty()) {
                            log.warn("[SupervisorWorker] 所有子任务未分配 Worker，进入降级模式, collaborationId={}", collaborationId);
                            return fallbackToSupervisor(supervisor, input, traceId);
                        }

                        return Flux.fromIterable(validSubTasks)
                                .flatMap(subTask -> dispatchToWorker(subTask, supervisor, workers,
                                        messageBus, sharedContext, collaborationId, traceId))
                                .collectList()
                                .flatMapMany(results -> aggregateResults(results, supervisor, input,
                                        sharedContext, collaborationId, traceId));
                    })
                    .doFinally(signal -> {
                        // 清理订阅
                        subscriptions.forEach((id, d) -> d.dispose());
                        subscriptions.clear();
                        workers.forEach(w -> messageBus.unsubscribe(w.getAgentId()));
                        messageBus.unsubscribe(supervisor.getAgentId());
                        log.info("[SupervisorWorker] 协作完成, collaborationId={}, signal={}", collaborationId, signal);
                    });
        });
    }

    /**
     * Supervisor 拆解任务。
     * 通过 LLM Function Calling 输出结构化的 TaskDecompositionResult。
     * 此处使用简化实现：直接调用 Supervisor 的 LLM 进行拆解。
     */
    private Mono<TaskDecompositionResult> decomposeTask(AgentConfig supervisor, String input,
                                                         SharedContext sharedContext,
                                                         String collaborationId, String traceId) {
        return Mono.fromCallable(() -> {
            log.info("[SupervisorWorker] Supervisor {} 开始拆解任务, collaborationId={}",
                    supervisor.getAgentId(), collaborationId);

            // 简化实现：构造一个基本的拆解结果
            // 实际生产环境中，这里会通过 LLM Function Calling 输出 TaskDecompositionResult
            List<TaskDecompositionResult.SubTask> subTasks = new ArrayList<>();
            if (supervisor.getDelegateTo() != null && !supervisor.getDelegateTo().isEmpty()) {
                int i = 0;
                for (String workerId : supervisor.getDelegateTo()) {
                    subTasks.add(TaskDecompositionResult.SubTask.builder()
                            .taskId("task-" + (i + 1))
                            .description("子任务 " + (i + 1) + ": " + input)
                            .assignedWorkerId(workerId)
                            .input(Map.of("query", input))
                            .priority(i + 1)
                            .build());
                    i++;
                }
            }

            TaskDecompositionResult result = TaskDecompositionResult.builder()
                    .originalInput(input)
                    .subTasks(subTasks)
                    .strategy("supervisor-worker")
                    .build();

            log.info("[SupervisorWorker] 任务拆解完成: {} 个子任务, collaborationId={}",
                    subTasks.size(), collaborationId);
            return result;
        });
    }

    /**
     * 分发子任务给 Worker，通过 requestReply 等待结果。
     */
    private Mono<WorkerResult> dispatchToWorker(TaskDecompositionResult.SubTask subTask,
                                                 AgentConfig supervisor,
                                                 List<AgentConfig> workers,
                                                 MessageBus messageBus,
                                                 SharedContext sharedContext,
                                                 String collaborationId, String traceId) {
        String workerId = subTask.getAssignedWorkerId();
        log.info("[SupervisorWorker] 分发子任务: taskId={} -> workerId={}, collaborationId={}",
                subTask.getTaskId(), workerId, collaborationId);

        AgentMessage taskMsg = AgentMessage.builder()
                .fromAgentId(supervisor.getAgentId())
                .toAgentId(workerId)
                .type(AgentMessage.MessageType.TASK_ASSIGNMENT)
                .content(subTask.getDescription())
                .payload(Map.of("subTaskId", subTask.getTaskId(), "input", subTask.getInput()))
                .collaborationId(collaborationId)
                .traceId(traceId)
                .build();

        return messageBus.requestReply(taskMsg, requestTimeout)
                .map(reply -> {
                    // 解析 Worker 的回复为 WorkerResult
                    WorkerResult result = WorkerResult.builder()
                            .taskId(subTask.getTaskId())
                            .workerId(workerId)
                            .status(WorkerResult.Status.SUCCESS)
                            .content(reply.getContent())
                            .durationMs(0)
                            .build();
                    log.info("[SupervisorWorker] Worker {} 完成子任务: taskId={}, status={}, collaborationId={}",
                            workerId, subTask.getTaskId(), result.getStatus(), collaborationId);
                    collabMetrics.recordWorkerResult(workerId, true);
                    return result;
                })
                .onErrorResume(error -> {
                    log.warn("[SupervisorWorker] Worker {} 子任务失败: taskId={}, error={}, collaborationId={}",
                            workerId, subTask.getTaskId(), error.getMessage(), collaborationId);

                    WorkerResult failedResult = WorkerResult.builder()
                            .taskId(subTask.getTaskId())
                            .workerId(workerId)
                            .status(WorkerResult.Status.TIMEOUT)
                            .errorMessage("Worker 超时或失败: " + error.getMessage())
                            .errorInfo(WorkerResult.ErrorInfo.builder()
                                    .errorCode("WORKER_TIMEOUT")
                                    .description(error.getMessage())
                                    .retryable(true)
                                    .retryAfterMs(1000)
                                    .build())
                            .durationMs(requestTimeout.toMillis())
                            .build();
                    collabMetrics.recordWorkerResult(workerId, false);
                    return Mono.just(failedResult);
                });
    }

    /**
     * 汇总所有 WorkerResult，生成最终回复。
     */
    private Flux<StreamEvent> aggregateResults(List<WorkerResult> results,
                                                AgentConfig supervisor,
                                                String input,
                                                SharedContext sharedContext,
                                                String collaborationId, String traceId) {
        long successCount = results.stream()
                .filter(r -> r.getStatus() == WorkerResult.Status.SUCCESS).count();
        long failCount = results.size() - successCount;

        log.info("[SupervisorWorker] 汇总结果: 成功={}, 失败={}, collaborationId={}",
                successCount, failCount, collaborationId);

        // 写入共享上下文
        sharedContext.put("supervisor/aggregated_results", results,
                supervisor.getAgentId(), "supervisor");

        StringBuilder summary = new StringBuilder();
        summary.append("## 任务执行汇总\n\n");
        summary.append("共 ").append(results.size()).append(" 个子任务，")
                .append(successCount).append(" 个成功");

        if (failCount > 0) {
            summary.append("，").append(failCount).append(" 个失败");
            collabMetrics.recordCollaborationCompleted();
        } else {
            collabMetrics.recordCollaborationCompleted();
        }
        summary.append("。\n\n");

        for (WorkerResult result : results) {
            summary.append("### ").append(result.getWorkerId())
                    .append(" (").append(result.getStatus()).append(")\n");
            if (result.getContent() != null) {
                summary.append(result.getContent()).append("\n\n");
            }
            if (result.getErrorMessage() != null) {
                summary.append("> 错误: ").append(result.getErrorMessage()).append("\n\n");
            }
        }

        return Flux.just(
                StreamEvent.builder().type(StreamEvent.Type.TEXT).content(summary.toString()).build(),
                StreamEvent.builder().type(StreamEvent.Type.DONE).build()
        );
    }

    /**
     * 降级模式：Supervisor 自行处理所有任务。
     */
    private Flux<StreamEvent> fallbackToSupervisor(AgentConfig supervisor, String input, String traceId) {
        log.warn("[SupervisorWorker] 进入降级模式: Supervisor 自行处理, traceId={}", traceId);
        return Flux.just(
                StreamEvent.builder()
                        .type(StreamEvent.Type.THINKING)
                        .content("任务拆解失败，Supervisor 自行处理...")
                        .build(),
                StreamEvent.builder()
                        .type(StreamEvent.Type.TEXT)
                        .content("[降级模式] Supervisor 处理结果: " + input)
                        .build(),
                StreamEvent.builder().type(StreamEvent.Type.DONE).build()
        );
    }

    private AgentConfig findSupervisor(List<AgentConfig> agents) {
        return agents.stream()
                .filter(a -> "supervisor".equals(a.getRole()))
                .findFirst()
                .orElse(null);
    }

    private List<AgentConfig> findWorkers(List<AgentConfig> agents) {
        return agents.stream()
                .filter(a -> "worker".equals(a.getRole()))
                .collect(Collectors.toList());
    }
}
