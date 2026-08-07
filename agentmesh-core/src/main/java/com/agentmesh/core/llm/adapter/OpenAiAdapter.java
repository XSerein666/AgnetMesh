package com.agentmesh.core.llm.adapter;

import com.agentmesh.core.llm.LlmChatResponse;
import com.agentmesh.core.llm.StreamEvent;
import com.agentmesh.core.llm.ToolCallRequest;
import com.agentmesh.core.llm.ToolChoice;
import com.agentmesh.core.llm.ToolDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * OpenAI 标准格式适配器
 */
@Slf4j
public class OpenAiAdapter implements ProviderAdapter {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String getName() {
        return "openai";
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> adaptTools(List<ToolDefinition> tools) {
        return tools.stream().map(tool -> {
            Map<String, Object> t = new LinkedHashMap<>();
            t.put("type", "function");
            Map<String, Object> function = new LinkedHashMap<>();
            function.put("name", tool.getName());
            function.put("description", tool.getDescription());
            function.put("parameters", tool.getParameters());
            t.put("function", function);
            return t;
        }).collect(Collectors.toList());
    }

    @Override
    public Object adaptToolChoice(ToolChoice toolChoice) {
        if (toolChoice == null || toolChoice.isAuto()) {
            return "auto";
        }
        if (toolChoice.isRequired()) {
            return "required";
        }
        if (toolChoice.isNone()) {
            return "none";
        }
        if (toolChoice.isSpecific()) {
            return Map.of("type", "function",
                    "function", Map.of("name", toolChoice.getSpecificToolName()));
        }
        return "auto";
    }

    @Override
    @SuppressWarnings("unchecked")
    public LlmChatResponse adaptResponse(String rawResponseBody) {
        try {
            Map<String, Object> resp = objectMapper.readValue(rawResponseBody, Map.class);
            List<Map<String, Object>> choices = (List<Map<String, Object>>) resp.get("choices");
            if (choices == null || choices.isEmpty()) {
                return LlmChatResponse.builder().content("").finishReason("stop").build();
            }
            Map<String, Object> choice = choices.get(0);
            String finishReason = normalizeFinishReason((String) choice.get("finish_reason"));
            Map<String, Object> message = (Map<String, Object>) choice.get("message");

            if (message == null) {
                return LlmChatResponse.builder().content("").finishReason(finishReason).build();
            }

            List<ToolCallRequest> toolCalls = null;
            List<Map<String, Object>> rawToolCalls = (List<Map<String, Object>>) message.get("tool_calls");
            if (rawToolCalls != null && !rawToolCalls.isEmpty()) {
                toolCalls = new ArrayList<>();
                for (Map<String, Object> tc : rawToolCalls) {
                    ToolCallRequest tcr = new ToolCallRequest();
                    tcr.setId((String) tc.get("id"));
                    Map<String, Object> function = (Map<String, Object>) tc.get("function");
                    if (function != null) {
                        tcr.setName((String) function.get("name"));
                        String argsStr = (String) function.get("arguments");
                        if (argsStr != null) {
                            tcr.setArguments(objectMapper.readValue(argsStr, Map.class));
                        }
                    }
                    toolCalls.add(tcr);
                }
            }

            String content = (String) message.get("content");
            return LlmChatResponse.builder()
                    .content(content)
                    .toolCalls(toolCalls)
                    .finishReason(finishReason)
                    .build();
        } catch (Exception e) {
            log.error("[OpenAiAdapter] 响应解析失败", e);
            return LlmChatResponse.builder()
                    .content("")
                    .finishReason("stop")
                    .build();
        }
    }

    @Override
    public String normalizeFinishReason(String rawFinishReason) {
        if (rawFinishReason == null) {
            return "stop";
        }
        return switch (rawFinishReason) {
            case "stop" -> "stop";
            case "tool_calls", "function_call" -> "tool_calls";
            case "length" -> "length";
            default -> "stop";
        };
    }

    /**
     * OpenAI 流式 chunk 格式：
     *   data: {"choices":[{"delta":{"content":"...","tool_calls":[...]}, "finish_reason":null|"tool_calls"}]}
     *   data: [DONE]
     *
     * 与 DashScope 差异：
     *   - choices 在根级而非 output.choices
     *   - delta 而非 message
     *   - finish_reason 在 choices[0] 上
     */
    @Override
    @SuppressWarnings("unchecked")
    public StreamEvent adaptStreamChunk(String rawChunk, ObjectMapper mapper) {
        try {
            Map<String, Object> chunk = mapper.readValue(rawChunk, Map.class);
            List<Map<String, Object>> choices = (List<Map<String, Object>>) chunk.get("choices");
            if (choices == null || choices.isEmpty()) {
                return null;
            }

            Map<String, Object> choice = choices.get(0);
            String finishReason = (String) choice.get("finish_reason");
            Map<String, Object> delta = (Map<String, Object>) choice.get("delta");
            if (delta == null) {
                // 无 delta 但有 finish_reason=tool_calls，发 END
                if ("tool_calls".equals(finishReason)) {
                    return StreamEvent.builder()
                            .type(StreamEvent.Type.TOOL_CALL_END)
                            .build();
                }
                return null;
            }

            // 工具调用增量
            List<Map<String, Object>> toolCalls = (List<Map<String, Object>>) delta.get("tool_calls");
            if (toolCalls != null && !toolCalls.isEmpty()) {
                Map<String, Object> tc = toolCalls.get(0);
                Map<String, Object> function = (Map<String, Object>) tc.get("function");
                if (function == null) {
                    return null;
                }

                String name = (String) function.get("name");
                String arguments = (String) function.get("arguments");

                // 有 name → 工具调用开始（可能同时携带首段 arguments）
                if (name != null && !name.isEmpty()) {
                    return StreamEvent.builder()
                            .type(StreamEvent.Type.TOOL_CALL_START)
                            .toolName(name)
                            .toolCallId((String) tc.get("id"))
                            .content(arguments != null ? arguments : "")
                            .build();
                }

                // 有 arguments 增量
                if (arguments != null && !arguments.isEmpty()) {
                    return StreamEvent.builder()
                            .type(StreamEvent.Type.TOOL_CALL_ARGS)
                            .content(arguments)
                            .build();
                }
            }

            // finish_reason 为 tool_calls 且无更多 delta 数据
            if ("tool_calls".equals(finishReason)) {
                return StreamEvent.builder()
                        .type(StreamEvent.Type.TOOL_CALL_END)
                        .build();
            }

            // 文本内容
            String content = (String) delta.get("content");
            if (content != null && !content.isEmpty()) {
                return StreamEvent.builder()
                        .type(StreamEvent.Type.TEXT)
                        .content(content)
                        .build();
            }

            return null;
        } catch (Exception e) {
            log.warn("[OpenAiAdapter] 流式 chunk 解析失败: {}", rawChunk, e);
            return null;
        }
    }
}
