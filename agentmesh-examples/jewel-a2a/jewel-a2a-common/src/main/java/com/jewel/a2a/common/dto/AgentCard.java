package com.jewel.a2a.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Agent 名片（对应 /.well-known/agent.json 响应）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AgentCard {
    private String agentId;
    private String name;
    private String description;
    private String version;
    private List<AgentSkill> skills;
}