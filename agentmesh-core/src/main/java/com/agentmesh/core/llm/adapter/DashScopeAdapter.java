package com.agentmesh.core.llm.adapter;

import com.agentmesh.core.llm.LlmChatResponse;
import com.agentmesh.core.llm.StreamEvent;
import com.agentmesh.core.llm.ToolCallRequest;
import com.agentmesh.core.llm.ToolChoice;
import com.agentmesh.core.llm.ToolDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 百炼 DashScope 专用格式适配器
 * 处理 output.choices[0].message.tool_calls 差异
 */
@Slf4j
public class DashScopeAdapter implements ProviderAdapter {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String getName() {
        return "dashscope";
    }

    @Override
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
            Map<String, Object> output = (Map<String, Object>) resp.get("output");
            if (output == null) {
                return LlmChatResponse.builder().content("").finishReason("stop").build();
            }

            // 检查 choices 格式（原生 function calling 响应）
            List<Map<String, Object>> choices = (List<Map<String, Object>>) output.get("choices");
            if (choices != null && !choices.isEmpty()) {
                Map<String, Object> choice = choices.get(0);
                String finishReason = normalizeFinishReason((String) choice.get("finish_reason"));
                Map<String, Object> message = (Map<String, Object>) choice.get("message");

                if (message != null) {
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
                }
            }

            // 回退到纯文本格式
            String text = (String) output.get("text");
            return LlmChatResponse.builder()
                    .content(text)
                    .finishReason("stop")
                    .build();
        } catch (Exception e) {
            log.error("[DashScopeAdapter] 响应解析失败", e);
            return LlmChatResponse.builder()
                    .content("")
                    .finishReason("stop")
                    .build();
        }
    }

    @Override
    public String normalizeFinishReason(String rawFinishReason) {
        if (rawFinishReason == null) return "stop";
        return switch (rawFinishReason) {
            case "stop" -> "stop";
            case "tool_calls", "function_call" -> "tool_calls";
            case "length" -> "length";
            default -> "stop";
        };
    }

    @Override
    @SuppressWarnings("unchecked")
    public StreamEvent adaptStreamChunk(String rawChunk, ObjectMapper mapper) {
        try {
            log.info("[DashScopeAdapter] 流式原始 chunk: {}", rawChunk);
            Map<String, Object> chunk = mapper.readValue(rawChunk, Map.class);
            Map<String, Object> output = (Map<String, Object>) chunk.get("output");
            if (output == null) return null;

            List<Map<String, Object>> choices = (List<Map<String, Object>>) output.get("choices");
            if (choices == null || choices.isEmpty()) return null;

            Map<String, Object> choice = choices.get(0);
            String finishReason = (String) choice.get("finish_reason");
            Map<String, Object> message = (Map<String, Object>) choice.get("message");
            if (message == null) return null;

            // 检查 tool_calls
            List<Map<String, Object>> toolCalls = (List<Map<String, Object>>) message.get("tool_calls");
            if (toolCalls != null && !toolCalls.isEmpty()) {
                Map<String, Object> tc = toolCalls.get(0);
                Map<String, Object> function = (Map<String, Object>) tc.get("function");
                if (function == null) return null;

                String name = (String) function.get("name");
                String arguments = (String) function.get("arguments");

                // 有 name 说明是 tool_call_start（可能同时包含首段 arguments，放在 content 中）
                if (name != null && !name.isEmpty()) {
                    log.info("[DashScopeAdapter] 工具调用开始: name={}, id={}, hasArgs={}",
                            name, tc.get("id"), arguments != null && !arguments.isEmpty());
                    return StreamEvent.builder()
                            .type(StreamEvent.Type.TOOL_CALL_START)
                            .toolName(name)
                            .toolCallId((String) tc.get("id"))
                            .content(arguments)  // 首段 arguments 带上，避免丢失
                            .build();
                }

                // 有 arguments 说明是参数增量（优先于 finish_reason，避免丢失最后一段参数）
                if (arguments != null && !arguments.isEmpty()) {
                    return StreamEvent.builder()
                            .type(StreamEvent.Type.TOOL_CALL_ARGS)
                            .content(arguments)
                            .build();
                }

                // 当 finish_reason 为 tool_calls 且无 arguments 时，发送 TOOL_CALL_END
                if ("tool_calls".equals(finishReason)) {
                    log.info("[DashScopeAdapter] 工具调用结束: name={}", name);
                    return StreamEvent.builder()
                            .type(StreamEvent.Type.TOOL_CALL_END)
                            .toolName(name)
                            .toolCallId((String) tc.get("id"))
                            .build();
                }
                return null;
            }

            // 纯文本的 finish_reason 为 tool_calls（无 tool_calls 数组时，可能是流结束信号）
            if ("tool_calls".equals(finishReason)) {
                return StreamEvent.builder()
                        .type(StreamEvent.Type.TOOL_CALL_END)
                        .build();
            }

            // 文本内容
            String content = (String) message.get("content");
            if (content != null && !content.isEmpty()) {
                return StreamEvent.builder()
                        .type(StreamEvent.Type.TEXT)
                        .content(content)
                        .build();
            }
            return null;
        } catch (Exception e) {
            log.warn("[DashScopeAdapter] 流式 chunk 解析失败: {}", rawChunk, e);
            return null;
        }
    }
}