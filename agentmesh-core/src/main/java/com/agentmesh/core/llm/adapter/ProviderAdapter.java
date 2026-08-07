package com.agentmesh.core.llm.adapter;

import com.agentmesh.core.llm.LlmChatResponse;
import com.agentmesh.core.llm.ToolChoice;
import com.agentmesh.core.llm.ToolDefinition;
import com.agentmesh.core.llm.StreamEvent;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

/**
 * 厂商适配器：处理不同厂商的 API 差异
 */
public interface ProviderAdapter {

    /** 适配器名称 */
    String getName();

    /** 将框架的 ToolDefinition 转换为厂商专用 tools 参数格式 */
    List<Map<String, Object>> adaptTools(List<ToolDefinition> tools);

    /** 将 tool_choice 转换为厂商专用格式 */
    Object adaptToolChoice(ToolChoice toolChoice);

    /** 将厂商原始响应转换为框架统一的 LlmChatResponse */
    LlmChatResponse adaptResponse(String rawResponseBody);

    /** 将厂商的 finish_reason 映射为框架标准值 */
    String normalizeFinishReason(String rawFinishReason);

    /**
     * 解析流式响应中的单个 SSE chunk 为 StreamEvent
     * @param rawChunk 单行 JSON 数据
     * @param objectMapper 用于 JSON 解析
     * @return 解析后的 StreamEvent，无法识别时返回 null
     */
    default StreamEvent adaptStreamChunk(String rawChunk, ObjectMapper objectMapper) {
        return null;
    }
}
