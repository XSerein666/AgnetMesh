package com.agentmesh.core.llm;

import java.util.List;
import java.util.Map;
import reactor.core.publisher.Flux;

/**
 * LLM 客户端接口
 */
public interface LlmClient {

    // ========== 基础方法 ==========

    /**
     * 基础文本对话（无工具），用于降级场景
     * @param messages 消息列表，每项包含 role 和 content
     * @return 模型回复文本
     */
    String chat(List<Map<String, Object>> messages);

    /**
     * 带工具调用的对话
     * @param messages   消息列表
     * @param tools      可用工具定义列表
     * @param toolChoice 工具调用策略
     * @return 统一响应（包含文本或工具调用）
     */
    LlmChatResponse chatWithTools(List<Map<String, Object>> messages,
                                   List<ToolDefinition> tools,
                                   ToolChoice toolChoice);

    /**
     * 是否支持原生 function calling
     */
    boolean supportsFunctionCalling();

    /**
     * 是否支持 role:"tool" 消息类型（降级模式下使用，默认 false）
     */
    default boolean supportsToolRole() {
        return false;
    }

    /**
     * 多模态视觉对话（可选实现）
     */
    default String vision(String imageUrl, String prompt) {
        throw new UnsupportedOperationException("Vision not supported by this LLM client");
    }

    // ========== 流式方法 ==========

    /**
     * 流式文本对话
     * @return 文本片段流，每个片段是一段增量文本
     */
    default Flux<String> chatStream(List<Map<String, Object>> messages) {
        throw new UnsupportedOperationException("Streaming not supported");
    }

    /**
     * 流式带工具调用对话
     * @return 事件流：TEXT(文本增量) / TOOL_CALL_START / TOOL_CALL_ARGS(参数增量) / TOOL_CALL_END
     */
    default Flux<StreamEvent> chatWithToolsStream(List<Map<String, Object>> messages,
                                                   List<ToolDefinition> tools,
                                                   ToolChoice toolChoice) {
        throw new UnsupportedOperationException("Streaming not supported");
    }

    // ========== Token 估算 ==========

    /**
     * 获取模型专属的 Token 估算器
     */
    default TokenEstimator getTokenEstimator() {
        return text -> text.length(); // 默认按字符数估算
    }
}
