package com.agentmesh.core.planning;

import reactor.core.publisher.Flux;

/**
 * 计划执行器接口
 */
public interface PlanExecutor {

    /**
     * 执行计划，返回聚合结果
     */
    PlanResult execute(TaskPlan plan, java.util.function.Function<SubTask, String> subTaskRunner);

    /**
     * 流式执行，推送每步结果
     */
    Flux<PlanStepEvent> executeStream(TaskPlan plan,
                                       java.util.function.Function<SubTask, String> subTaskRunner);
}