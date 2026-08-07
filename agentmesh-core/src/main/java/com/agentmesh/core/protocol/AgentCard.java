package com.agentmesh.core.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Agent 名片（对应 /.well-known/agent.json 响应）
 *
 * 字段说明：
 * - name/description/url/version/skills 为 Google A2A 规范字段
 * - agentId 为框架私有扩展字段，非 A2A 规范
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AgentCard {
    // === Google A2A 规范字段 ===
    private String name;
    private String description;
    private String url;
    private String version;
    private List<AgentSkill> skills;

    // === 框架私有扩展字段 ===
    private String agentId;
}
