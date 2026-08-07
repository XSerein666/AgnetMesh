package com.agentmesh.core.agent;

import com.agentmesh.core.infrastructure.AgentMeshMetrics;
import com.agentmesh.core.infrastructure.TraceIdContext;
import com.agentmesh.core.llm.StreamEvent;
import com.agentmesh.core.remote.AgentClient;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

import java.util.Collections;
import java.util.List;

/**
 * 并行编排器：同时启动 plan 中的所有 Agent，事件按到达顺序透传。
 *
 * 并行策略：
 * - 使用 Flux.merge() 合并多个 Agent 的事件流，事件按到达顺序交错发射
 * - 每个事件加前缀 [Agent:{agentId}] 区分来源
 * - 本地和远程 Agent 混合支持
 * - 所有 Agent 完成后统一发射一个 DONE 事件
 *
 * 本地/远程分支：
 * - 本地 Agent（agentUrl 为空）：通过 ReActAgentFactory 创建实例，本地执行 ReAct 循环
 * - 远程 Agent（agentUrl 非空）：通过 AgentClient.callSkillStream() 流式调用，SSE 事件透传
 */
@Slf4j
public class ParallelOrchestrator implements AgentOrchestrator {

    private final SequentialAgentOrchestrator.ReActAgentFactory agentFactory;
    private final AgentClient agentClient;
    private final AgentMeshMetrics metrics;

    public ParallelOrchestrator(SequentialAgentOrchestrator.ReActAgentFactory agentFactory,
                                AgentClient agentClient) {
        this(agentFactory, agentClient, null);
    }

    public ParallelOrchestrator(SequentialAgentOrchestrator.ReActAgentFactory agentFactory,
                                AgentClient agentClient, AgentMeshMetrics metrics) {
        this.agentFactory = agentFactory;
        this.agentClient = agentClient;
        this.metrics = metrics;
    }

    /**
     * 同步编排入口。
     * 注意：内部使用 .block() 阻塞等待，仅限非反应式线程调用。
     */
    @Override
    public OrchestrationResult orchestrate(OrchestrationPlan plan, String input) {
        return orchestrateStream(plan, input)
                .collectList()
                .map(events -> {
                    OrchestrationResult result = new OrchestrationResult();
                    result.setSuccess(true);
                    String finalOutput = events.stream()
                            .filter(e -> e.getType() == StreamEvent.Type.TEXT)
                            .reduce((first, second) -> second)
                            .map(StreamEvent::getContent)
                            .orElse("");
                    result.setFinalOutput(finalOutput);
                    return result;
                })
                .block();
    }

    @Override
    public Flux<StreamEvent> orchestrateStream(OrchestrationPlan plan, String input) {
        List<AgentConfig> agents = plan.getAgents();
        if (agents.isEmpty()) {
            return Flux.just(StreamEvent.builder()
                    .type(StreamEvent.Type.ERROR)
                    .content("编排计划中没有 Agent")
                    .build());
        }

        // 从 servlet 线程捕获 traceId，后续显式传递（不在 reactor 线程中操作 ThreadLocal）
        String traceId = TraceIdContext.get();

        log.info("[ParallelOrchestrator] 并行启动 {} 个 Agent, traceId={}", agents.size(), traceId);

        List<Flux<StreamEvent>> agentFluxes = agents.stream()
                .map(config -> executeAgent(config, input, traceId))
                .toList();

        return Flux.merge(agentFluxes)
                .concatWith(Flux.just(StreamEvent.builder()
                        .type(StreamEvent.Type.DONE)
                        .build()));
    }

    /**
     * 执行单个 Agent（本地或远程），返回其事件流。
     * traceId 显式传入，不在 reactor 线程中读 ThreadLocal。
     */
    private Flux<StreamEvent> executeAgent(AgentConfig config, String input, String traceId) {
        String label = config.getAgentId() != null && !config.getAgentId().isEmpty()
                ? config.getAgentId() : "unnamed";
        String prefix = "[Agent:" + label + "] ";

        Timer.Sample sample = metrics != null ? metrics.startAgentTimer() : null;
        log.info("[ParallelOrchestrator] Agent 启动: agentId={}, remote={}, traceId={}",
                label, config.isRemote(), traceId);

        if (config.isRemote()) {
            log.info("[ParallelOrchestrator] 远程 Agent: {} @ {}", label, config.getAgentUrl());
            // traceId 显式传入，由 HttpAgentClient 负责非空时才设 X-Trace-Id 头
            return agentClient.callSkillStream(config.getAgentUrl(), input, traceId, label)
                    .map(event -> prefixEvent(event, prefix))
                    .doOnComplete(() -> recordAgentComplete(label, "SUCCESS", sample))
                    .onErrorResume(e -> {
                        log.error("[ParallelOrchestrator] 远程 Agent 异常: {}", label, e);
                        recordAgentComplete(label, "FAILED", sample);
                        return Flux.just(StreamEvent.builder()
                                .type(StreamEvent.Type.ERROR)
                                .content(prefix + "远程调用失败: " + e.getMessage())
                                .build());
                    });
        }

        log.info("[ParallelOrchestrator] 本地 Agent: {}", label);
        ReActAgent agent = agentFactory.create(config);
        return agent.runStream(config.resolveSystemPrompt(), input, Collections.emptyList())
                .map(event -> prefixEvent(event, prefix))
                .doOnComplete(() -> recordAgentComplete(label, "SUCCESS", sample))
                .doOnError(e -> {
                    log.error("[ParallelOrchestrator] 本地 Agent 异常: {}", label, e);
                    recordAgentComplete(label, "FAILED", sample);
                });
    }

    private void recordAgentComplete(String agentId, String status, Timer.Sample sample) {
        if (metrics != null) {
            metrics.recordAgentInvocation(agentId, status);
            metrics.stopAgentTimer(sample, agentId);
        }
        log.info("[ParallelOrchestrator] Agent 完成: agentId={}, status={}", agentId, status);
    }

    /**
     * 给事件内容加上 Agent 前缀，便于客户端区分来源。
     */
    private StreamEvent prefixEvent(StreamEvent event, String prefix) {
        if (event.getType() == StreamEvent.Type.TEXT && event.getContent() != null) {
            return StreamEvent.builder()
                    .type(event.getType())
                    .content(prefix + event.getContent())
                    .toolName(event.getToolName())
                    .toolCallId(event.getToolCallId())
                    .arguments(event.getArguments())
                    .result(event.getResult())
                    .build();
        }
        if (event.getType() == StreamEvent.Type.THINKING && event.getContent() != null) {
            return StreamEvent.builder()
                    .type(event.getType())
                    .content(prefix + event.getContent())
                    .build();
        }
        return event;
    }
}
