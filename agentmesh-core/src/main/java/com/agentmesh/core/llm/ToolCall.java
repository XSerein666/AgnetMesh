package com.agentmesh.core.llm;

import lombok.Data;
import java.util.Map;

/**
 * 降级模式下的工具调用解析结果
 * 用于 ReActAgent 降级模式的 JSON 解析
 */
@Data
public class ToolCall {
    private String tool;
    private Map<String, Object> input;
}
