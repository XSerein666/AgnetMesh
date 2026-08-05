package com.agentmesh.core.registry;

import com.agentmesh.core.protocol.AgentCard;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentNode {
    /** Agent 名片 */
    private AgentCard card;
    /** 注册/发现时间 */
    private LocalDateTime registeredAt;
    /** 最后刷新时间 */
    private LocalDateTime lastRefreshedAt;
    /** 是否可达 */
    @Builder.Default
    private boolean reachable = true;

    // === Phase 13：自动生成的路由元数据（volatile，异步写 + 路由线程读） ===
    /** LLM 自动生成的中文描述 */
    private volatile String generatedDescription;
    /** LLM 自动生成的路由标签 */
    private volatile List<String> generatedRoutingTags;
    /** 生成时 AgentCard.skills 的指纹（用于判断是否需要重新生成） */
    private volatile String skillsFingerprint;
}