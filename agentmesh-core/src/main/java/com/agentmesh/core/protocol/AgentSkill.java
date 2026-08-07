package com.agentmesh.core.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 单个 Tool 的描述
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AgentSkill {
    private String id;
    private String name;
    private String description;
    private Map<String, Object> inputSchema;

    // === 框架私有扩展字段 ===
    private String agentId;
}
