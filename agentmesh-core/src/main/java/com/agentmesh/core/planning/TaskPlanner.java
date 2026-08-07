package com.agentmesh.core.planning;

import com.agentmesh.core.llm.ToolDefinition;

import java.util.List;

/**
 * 任务规划接口
 */
public interface TaskPlanner {

    /**
     * 生成任务计划
     * @param goal    用户目标
     * @param tools   可用工具列表
     * @param context 上下文（长期记忆、当前对话等）
     * @return 任务计划，失败时返回空计划
     */
    TaskPlan plan(String goal, List<ToolDefinition> tools, String context);
}
