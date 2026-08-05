package com.agentmesh.core.llm;

import lombok.Builder;
import lombok.Data;
import java.util.Map;

/**
 * 工具定义（给 LLM 看的工具描述）
 */
@Data
@Builder
public class ToolDefinition {
    /** 工具名称，对应 Tool.getId() */
    private String name;
    /** 工具描述 */
    private String description;
    /** 参数 JSON Schema */
    private Map<String, Object> parameters;
}