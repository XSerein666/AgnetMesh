package com.agentmesh.core.agent;

import java.util.List;

/**
 * 编排计划：描述 Agent 之间的协作拓扑
 */
public interface OrchestrationPlan {
    List<AgentConfig> getAgents();
    ExecutionMode getMode(); // SEQUENTIAL / PARALLEL / CONDITIONAL
    String getRoutingRule(); // 条件路由时的判断规则
}