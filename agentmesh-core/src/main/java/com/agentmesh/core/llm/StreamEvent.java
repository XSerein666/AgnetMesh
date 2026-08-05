package com.agentmesh.core.llm;

import lombok.Builder;
import lombok.Data;
import java.util.Map;

/**
 * 流式事件类型
 */
@Data
@Builder
public class StreamEvent {
    public enum Type {
        THINKING,          // Agent 推理中
        TEXT,              // 文本增量片段
        TOOL_CALL_START,   // 开始工具调用 {id, name}
        TOOL_CALL_ARGS,    // 工具参数增量
        TOOL_CALL_END,     // 工具调用结束
        TOOL_RESULT,       // 工具执行结果
        DONE,              // 流结束
        ERROR              // 错误
    }

    private Type type;
    private String content;         // THINKING / TEXT / TOOL_CALL_ARGS 时使用
    private String toolCallId;      // TOOL_CALL_* 时使用
    private String toolName;        // TOOL_CALL_START / TOOL_RESULT 时使用
    private Map<String, Object> arguments; // TOOL_CALL_END 时使用（完整参数）
    private Object result;          // TOOL_RESULT 时使用（工具执行结果）
}