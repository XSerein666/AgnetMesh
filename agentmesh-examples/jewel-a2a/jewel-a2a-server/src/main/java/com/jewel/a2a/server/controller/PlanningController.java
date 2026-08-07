package com.jewel.a2a.server.controller;

import com.agentmesh.core.planning.PlanResult;
import com.agentmesh.core.planning.TaskPlan;
import com.jewel.a2a.server.service.PlanningService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 任务规划 API：将复杂需求分解为子任务 DAG 并执行。
 */
@RestController
@RequiredArgsConstructor
public class PlanningController {

    private final PlanningService planningService;

    /**
     * 任务规划：POST /a2a/plan
     * 将复杂需求分解为子任务 DAG。
     */
    @PostMapping("/a2a/plan")
    public TaskPlan planTask(@RequestBody PlanRequest request) {
        return planningService.plan(request.goal());
    }

    /**
     * 执行规划：POST /a2a/plan/execute
     * 执行已生成的计划。
     */
    @PostMapping("/a2a/plan/execute")
    public PlanResult executePlan(@RequestBody PlanExecuteRequest request) {
        return planningService.execute(request.planId());
    }

    public record PlanRequest(String goal, String sessionId) {}
    public record PlanExecuteRequest(String planId, String sessionId) {}
}