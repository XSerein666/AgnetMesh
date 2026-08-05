package com.agentmesh.core.routing;

import com.agentmesh.core.agent.AgentConfig;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * 关键词匹配路由策略。
 * 从 AgentConfig.routingRule 中解析 "关键词:索引" 对进行匹配。
 * 有匹配时返回单元素列表，confidence=1.0；无匹配时返回空列表（调用方兜底）。
 */
@Slf4j
public class KeywordRoutingStrategy implements RoutingStrategy {

    @Override
    public List<RankedAgent> route(String input, List<AgentConfig> candidates) {
        if (candidates.isEmpty()) {
            return List.of();
        }

        for (AgentConfig config : candidates) {
            String routingRule = config.getRoutingRule();
            if (routingRule != null && !routingRule.isEmpty()) {
                for (String rule : routingRule.split(",")) {
                    String[] parts = rule.trim().split(":");
                    if (parts.length == 2 && input.contains(parts[0].trim())) {
                        return List.of(RankedAgent.builder()
                                .agent(config)
                                .confidence(1.0)
                                .build());
                    }
                }
            }
        }

        return List.of(); // 无匹配，由调用方兜底
    }
}