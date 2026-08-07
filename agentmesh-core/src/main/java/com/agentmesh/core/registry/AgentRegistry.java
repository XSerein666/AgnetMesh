package com.agentmesh.core.registry;

import com.agentmesh.core.agent.AgentConfig;

import java.time.Duration;
import java.util.List;

/**
 * Agent 注册中心接口。
 * 本阶段提供 InMemoryAgentRegistry（点对点模式），
 * 后续可替换为独立注册中心实现（如 RedisRegistry、NacosRegistry）。
 * 替换方式：实现本接口并将 Bean 注册到 Spring 容器，@ConditionalOnMissingBean 自动覆盖默认实现。
 *
 * 注意：register/deregister/heartbeat 等注册中心专属方法暂不定义，
 * 等 Phase 独立注册中心阶段再扩展本接口。
 */
public interface AgentRegistry {

    /** 发现所有已注册 Agent */
    List<AgentNode> discover();

    /** 按技能 ID 发现 Agent */
    List<AgentNode> discoverBySkill(String skillId);

    /** 按 Agent ID 查找（未命中时惰性重拉对端） */
    AgentNode resolve(String agentId);

    /** 获取自身 Agent 信息 */
    AgentNode self();

    /**
     * 从注册中心构建 AgentConfig（含路由元数据）。
     * 优先使用自动生成的 description/routingTags，回退到 AgentCard.description。
     *
     * 这是路由候选构造的唯一入口，缓存预热和运行时路由都必须走此方法。
     */
    default AgentConfig buildAgentConfig(String agentId) {
        AgentNode node = resolve(agentId);
        if (node == null) {
            return null;
        }
        com.agentmesh.core.protocol.AgentCard card = node.getCard();
        return AgentConfig.builder()
                .agentId(agentId)
                .agentUrl(card.getUrl())
                .description(node.getGeneratedDescription() != null
                        ? node.getGeneratedDescription() : card.getDescription())
                .routingTags(node.getGeneratedRoutingTags())
                .skills(card.getSkills())
                .retryable(true)
                .build();
    }

    /**
     * 等待所有 in-flight descGen 任务完成（缓存预热就绪门控用）。
     * 默认实现：无 descGen 时直接返回 true。
     * @param timeout 最长等待时间
     * @return true = 全部完成，false = 超时
     */
    default boolean awaitDescGenCompletion(Duration timeout) {
        return true;
    }
}
