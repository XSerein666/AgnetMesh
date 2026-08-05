package com.agentmesh.core.llm;

import lombok.Builder;
import lombok.Data;
import java.util.List;

/**
 * 统一 LLM 响应
 */
@Data
@Builder
public class LlmChatResponse {
    /** 文本内容（无工具调用时） */
    private String content;
    /** 工具调用列表（有工具调用时） */
    private List<ToolCallRequest> toolCalls;
    /** 结束原因 */
    private String finishReason; // "stop" | "tool_calls" | "length"
}