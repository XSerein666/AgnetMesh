package com.jewel.a2a.server.service;

import com.agentmesh.core.planning.DagPlanExecutor;
import com.agentmesh.core.planning.PlanResult;
import com.agentmesh.core.planning.SubTask;
import com.agentmesh.core.planning.TaskPlan;
import com.agentmesh.core.planning.TaskPlanner;
import com.agentmesh.core.task.TaskRepository;
import com.agentmesh.core.tool.ToolRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 任务规划服务：将复杂需求分解为子任务 DAG 并执行。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlanningService {

    private final TaskPlanner taskPlanner;
    private final DagPlanExecutor dagPlanExecutor;
    private final ToolRegistry toolRegistry;
    private final TaskRepository taskRepository;
    private final Map<String, TaskPlan> planCache = new ConcurrentHashMap<>();

    /**
     * 分解复杂需求为子任务。
     */
    public TaskPlan plan(String goal) {
        log.info("[PlanningService] 开始规划: goal={}", goal);
        var toolDefinitions = toolRegistry.toDefinitions();
        TaskPlan plan = taskPlanner.plan(goal, toolDefinitions, "");
        planCache.put(plan.getPlanId(), plan);
        log.info("[PlanningService] 规划完成: planId={}, subtaskCount={}",
                plan.getPlanId(), plan.getSubTasks().size());
        return plan;
    }

    /**
     * 执行已生成的计划。
     */
    public PlanResult execute(String planId) {
        TaskPlan plan = planCache.get(planId);
        if (plan == null) {
            throw new IllegalArgumentException("Plan not found: " + planId);
        }
        log.info("[PlanningService] 开始执行: planId={}", planId);
        PlanResult result = dagPlanExecutor.execute(plan, this::runSubTask);
        log.info("[PlanningService] 执行完成: planId={}, success={}",
                planId, result.isAllSuccess());
        return result;
    }

    /**
     * 执行单个子任务：使用 ToolRegistry 执行建议的工具。
     */
    private String runSubTask(SubTask subTask) {
        String suggestedTool = subTask.getSuggestedTool();
        if (suggestedTool != null && toolRegistry.getTool(suggestedTool) != null) {
            try {
                Object result = toolRegistry.execute(suggestedTool,
                        Map.of("query", subTask.getDescription()));
                return result != null ? result.toString() : "工具执行完成";
            } catch (Exception e) {
                log.warn("[PlanningService] 子任务 {} 工具执行失败: {}", subTask.getId(), e.getMessage());
                return "工具执行失败: " + e.getMessage();
            }
        }
        return "子任务 " + subTask.getId() + " 已完成: " + subTask.getDescription();
    }
}
