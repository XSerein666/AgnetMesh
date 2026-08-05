package com.agentmesh.core.routing;

import com.agentmesh.core.agent.AgentConfig;

import java.util.List;

/**
 * 路由策略接口。
 * 根据用户输入从候选 Agent 中选出排序列表。
 *
 * 契约：元素不可为 null；无匹配时返回空列表（调用方自行兜底），
 * 而非返回任意候选——KeywordRoutingStrategy 的兜底行为由 ConditionalOrchestrator 实现。
 */
public interface RoutingStrategy {

    /**
     * 路由决策。
     * @param input      用户输入
     * @param candidates 候选 Agent 列表
     * @return 排序后的 Agent 列表（榜首为最佳匹配），元素不可为 null；无匹配时返回空列表
     */
    List<RankedAgent> route(String input, List<AgentConfig> candidates);
}