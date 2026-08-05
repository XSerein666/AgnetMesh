package com.agentmesh.core.planning;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * DAG 拓扑执行器。
 * <p>
 * 策略：
 * - 拓扑排序确定执行顺序
 * - 无依赖的子任务并发执行
 * - 子任务失败时，依赖它的后续任务不执行
 * - 总超时后终止
 */
@Slf4j
public class DagPlanExecutor implements PlanExecutor {

    private final ExecutorService executor;
    private final Duration timeout;
    private final boolean parallel;

    public DagPlanExecutor() {
        this(4, Duration.ofSeconds(120), true);
    }

    public DagPlanExecutor(int threadPoolSize, Duration timeout, boolean parallel) {
        this.executor = Executors.newFixedThreadPool(threadPoolSize);
        this.timeout = timeout;
        this.parallel = parallel;
    }

    @Override
    public PlanResult execute(TaskPlan plan, Function<SubTask, String> subTaskRunner) {
        if (plan.getSubTasks() == null || plan.getSubTasks().isEmpty()) {
            return PlanResult.builder()
                    .planId(plan.getPlanId())
                    .allSuccess(true)
                    .completedCount(0)
                    .failedCount(0)
                    .summary("")
                    .subTaskResults(List.of())
                    .build();
        }

        List<SubTask> subTasks = new ArrayList<>(plan.getSubTasks());
        Map<String, SubTask> taskMap = subTasks.stream()
                .collect(Collectors.toMap(SubTask::getId, t -> t));

        // 检测循环依赖
        if (hasCycle(subTasks, taskMap)) {
            log.error("[DagPlanExecutor] 检测到循环依赖，拒绝执行: planId={}", plan.getPlanId());
            return PlanResult.builder()
                    .planId(plan.getPlanId())
                    .allSuccess(false)
                    .completedCount(0)
                    .failedCount(subTasks.size())
                    .summary("计划存在循环依赖，无法执行")
                    .subTaskResults(subTasks.stream().map(t -> PlanResult.SubTaskResult.builder()
                            .subTaskId(t.getId())
                            .description(t.getDescription())
                            .status(SubTaskStatus.FAILED)
                            .error("循环依赖")
                            .build()).collect(Collectors.toList()))
                    .build();
        }

        long deadline = System.currentTimeMillis() + timeout.toMillis();
        List<PlanResult.SubTaskResult> results = new ArrayList<>();
        int completed = 0;
        int failed = 0;

        try {
            // BFS 逐层执行
            while (!subTasks.isEmpty()) {
                // 找出当前可执行的任务（所有依赖已完成）
                List<SubTask> ready = findReadyTasks(subTasks, results);
                if (ready.isEmpty()) {
                    // 无可用任务但还有未完成 → 可能因依赖失败导致死锁
                    long remaining = subTasks.size();
                    log.warn("[DagPlanExecutor] 无可用任务但仍有 {} 个未完成，终止", remaining);
                    for (SubTask t : subTasks) {
                        if (t.getStatus() == SubTaskStatus.PENDING) {
                            results.add(PlanResult.SubTaskResult.builder()
                                    .subTaskId(t.getId())
                                    .description(t.getDescription())
                                    .status(SubTaskStatus.FAILED)
                                    .error("依赖任务失败")
                                    .build());
                            failed++;
                            t.setStatus(SubTaskStatus.FAILED);
                        }
                    }
                    break;
                }

                // 执行
                if (parallel) {
                    List<Future<PlanResult.SubTaskResult>> futures = new ArrayList<>();
                    for (SubTask task : ready) {
                        task.setStatus(SubTaskStatus.RUNNING);
                        futures.add(executor.submit(() -> executeSubTask(task, subTaskRunner, deadline)));
                    }
                    for (Future<PlanResult.SubTaskResult> future : futures) {
                        try {
                            PlanResult.SubTaskResult result = future.get(
                                    Math.max(1, deadline - System.currentTimeMillis()),
                                    TimeUnit.MILLISECONDS);
                            results.add(result);
                            if (result.getStatus() == SubTaskStatus.DONE) completed++;
                            else failed++;
                        } catch (TimeoutException e) {
                            failed++;
                            results.add(PlanResult.SubTaskResult.builder()
                                    .subTaskId("unknown")
                                    .status(SubTaskStatus.FAILED)
                                    .error("超时")
                                    .build());
                        }
                    }
                } else {
                    for (SubTask task : ready) {
                        task.setStatus(SubTaskStatus.RUNNING);
                        PlanResult.SubTaskResult result = executeSubTask(task, subTaskRunner, deadline);
                        results.add(result);
                        if (result.getStatus() == SubTaskStatus.DONE) completed++;
                        else failed++;
                    }
                }

                // 检查总超时
                if (System.currentTimeMillis() > deadline) {
                    log.warn("[DagPlanExecutor] 总超时，终止剩余任务");
                    for (SubTask t : subTasks) {
                        if (t.getStatus() == SubTaskStatus.PENDING) {
                            t.setStatus(SubTaskStatus.FAILED);
                            results.add(PlanResult.SubTaskResult.builder()
                                    .subTaskId(t.getId())
                                    .description(t.getDescription())
                                    .status(SubTaskStatus.FAILED)
                                    .error("总超时")
                                    .build());
                            failed++;
                        }
                    }
                    break;
                }

                // 移除已完成的任务
                subTasks.removeIf(t -> t.getStatus() == SubTaskStatus.DONE
                        || t.getStatus() == SubTaskStatus.FAILED);
            }
        } catch (Exception e) {
            log.error("[DagPlanExecutor] 执行异常: planId={}", plan.getPlanId(), e);
        }

        String summary = buildSummary(plan.getGoal(), results);
        return PlanResult.builder()
                .planId(plan.getPlanId())
                .allSuccess(failed == 0)
                .completedCount(completed)
                .failedCount(failed)
                .summary(summary)
                .subTaskResults(results)
                .build();
    }

    @Override
    public Flux<PlanStepEvent> executeStream(TaskPlan plan,
                                              Function<SubTask, String> subTaskRunner) {
        if (plan.getSubTasks() == null || plan.getSubTasks().isEmpty()) {
            return Flux.empty();
        }
        return Flux.create(sink -> {
            try {
                PlanResult result = execute(plan, subTaskRunner);
                for (PlanResult.SubTaskResult sr : result.getSubTaskResults()) {
                    sink.next(PlanStepEvent.builder()
                            .planId(plan.getPlanId())
                            .subTaskId(sr.getSubTaskId())
                            .description(sr.getDescription())
                            .status(sr.getStatus())
                            .result(sr.getResult())
                            .error(sr.getError())
                            .build());
                }
                sink.complete();
            } catch (Exception e) {
                sink.error(e);
            }
        });
    }

    public void shutdown() {
        executor.shutdown();
    }

    // ========== 内部方法 ==========

    private PlanResult.SubTaskResult executeSubTask(SubTask task,
                                                     Function<SubTask, String> runner,
                                                     long deadline) {
        try {
            String result = runner.apply(task);
            task.setStatus(SubTaskStatus.DONE);
            task.setResult(result);
            return PlanResult.SubTaskResult.builder()
                    .subTaskId(task.getId())
                    .description(task.getDescription())
                    .status(SubTaskStatus.DONE)
                    .result(result)
                    .build();
        } catch (Exception e) {
            task.setStatus(SubTaskStatus.FAILED);
            String error = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            return PlanResult.SubTaskResult.builder()
                    .subTaskId(task.getId())
                    .description(task.getDescription())
                    .status(SubTaskStatus.FAILED)
                    .error(error)
                    .build();
        }
    }

    /**
     * 找出所有依赖已满足的待执行任务
     */
    private List<SubTask> findReadyTasks(List<SubTask> subTasks,
                                          List<PlanResult.SubTaskResult> results) {
        Set<String> completedIds = results.stream()
                .filter(r -> r.getStatus() == SubTaskStatus.DONE)
                .map(PlanResult.SubTaskResult::getSubTaskId)
                .collect(Collectors.toSet());
        Set<String> failedIds = results.stream()
                .filter(r -> r.getStatus() == SubTaskStatus.FAILED)
                .map(PlanResult.SubTaskResult::getSubTaskId)
                .collect(Collectors.toSet());

        return subTasks.stream()
                .filter(t -> t.getStatus() == SubTaskStatus.PENDING)
                .filter(t -> {
                    if (t.getDependsOn() == null || t.getDependsOn().isEmpty()) {
                        return true;
                    }
                    // 任一依赖失败，此任务不可执行
                    for (String depId : t.getDependsOn()) {
                        if (failedIds.contains(depId)) return false;
                    }
                    // 所有依赖已完成
                    return t.getDependsOn().stream().allMatch(completedIds::contains);
                })
                .collect(Collectors.toList());
    }

    /**
     * 检测 DAG 是否有循环依赖（DFS）
     */
    private boolean hasCycle(List<SubTask> subTasks, Map<String, SubTask> taskMap) {
        Set<String> visited = new HashSet<>();
        Set<String> inStack = new HashSet<>();

        for (SubTask task : subTasks) {
            if (dfs(task.getId(), taskMap, visited, inStack)) {
                return true;
            }
        }
        return false;
    }

    private boolean dfs(String nodeId, Map<String, SubTask> taskMap,
                         Set<String> visited, Set<String> inStack) {
        if (inStack.contains(nodeId)) return true;
        if (visited.contains(nodeId)) return false;

        visited.add(nodeId);
        inStack.add(nodeId);

        SubTask task = taskMap.get(nodeId);
        if (task != null && task.getDependsOn() != null) {
            for (String depId : task.getDependsOn()) {
                if (dfs(depId, taskMap, visited, inStack)) {
                    return true;
                }
            }
        }

        inStack.remove(nodeId);
        return false;
    }

    private String buildSummary(String goal, List<PlanResult.SubTaskResult> results) {
        StringBuilder sb = new StringBuilder();
        sb.append("目标: ").append(goal).append("\n\n");
        for (int i = 0; i < results.size(); i++) {
            PlanResult.SubTaskResult r = results.get(i);
            sb.append(i + 1).append(". ")
                    .append(r.getDescription())
                    .append(" [").append(r.getStatus()).append("]");
            if (r.getResult() != null && !r.getResult().isEmpty()) {
                sb.append("\n   ").append(r.getResult());
            }
            if (r.getError() != null) {
                sb.append("\n   错误: ").append(r.getError());
            }
            sb.append("\n");
        }
        return sb.toString();
    }
}