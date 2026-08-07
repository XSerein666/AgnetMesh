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
 * Ollama 格式适配器，处理 tools 字段扁平化差异
 */
@Slf4j
public class OllamaAdapter implements ProviderAdapter {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String getName() {
        return "ollama";
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
            Map<String, Object> message = (Map<String, Object>) resp.get("message");

            if (message == null) {
                return LlmChatResponse.builder().content("").finishReason("stop").build();
            }

            List<ToolCallRequest> toolCalls = null;
            List<Map<String, Object>> rawToolCalls = (List<Map<String, Object>>) message.get("tool_calls");
            if (rawToolCalls != null && !rawToolCalls.isEmpty()) {
                toolCalls = new ArrayList<>();
                for (Map<String, Object> tc : rawToolCalls) {
                    ToolCallRequest tcr = new ToolCallRequest();
                    Map<String, Object> function = (Map<String, Object>) tc.get("function");
                    if (function != null) {
                        tcr.setName((String) function.get("name"));
                        // Ollama 的 arguments 可能直接是 Map 而非 JSON 字符串
                        Object args = function.get("arguments");
                        if (args instanceof Map) {
                            tcr.setArguments((Map<String, Object>) args);
                        } else if (args instanceof String) {
                            tcr.setArguments(objectMapper.readValue((String) args, Map.class));
                        }
                    }
                    toolCalls.add(tcr);
                }
            }

            String content = (String) message.get("content");
            String finishReason = normalizeFinishReason((String) resp.get("done_reason"));
            return LlmChatResponse.builder()
                    .content(content)
                    .toolCalls(toolCalls)
                    .finishReason(finishReason)
                    .build();
        } catch (Exception e) {
            log.error("[OllamaAdapter] 响应解析失败", e);
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
     * Ollama 流式格式：每行一个完整 JSON（NDJSON，无 data: 前缀）。
     *   {"message":{"content":"..."},"done":false}
     *   {"message":{},"done":true,"done_reason":"stop"}
     *
     * 工具调用：Ollama 在最终行一次性返回完整 tool_calls，无流式增量。
     */
    @Override
    @SuppressWarnings("unchecked")
    public StreamEvent adaptStreamChunk(String rawChunk, ObjectMapper mapper) {
        try {
            Map<String, Object> chunk = mapper.readValue(rawChunk, Map.class);
            Map<String, Object> message = (Map<String, Object>) chunk.get("message");
            if (message == null) {
                return null;
            }

            // 文本内容增量
            String content = (String) message.get("content");
            if (content != null && !content.isEmpty()) {
                return StreamEvent.builder()
                        .type(StreamEvent.Type.TEXT)
                        .content(content)
                        .build();
            }

            // 工具调用（完整返回）
            List<Map<String, Object>> toolCalls = (List<Map<String, Object>>) message.get("tool_calls");
            if (toolCalls != null && !toolCalls.isEmpty()) {
                Map<String, Object> tc = toolCalls.get(0);
                Map<String, Object> function = (Map<String, Object>) tc.get("function");
                if (function == null) {
                    return null;
                }

                String name = (String) function.get("name");
                String arguments = (String) function.get("arguments");

                Map<String, Object> argsMap = null;
                if (arguments != null && !arguments.isEmpty()) {
                    try {
                        argsMap = mapper.readValue(arguments, Map.class);
                    } catch (Exception e) {
                        log.debug("[OllamaAdapter] arguments 不是合法 JSON，留给上层累积解析: {}", e.getMessage());
                    }
                }

                // Ollama 一次性返回完整调用，发 START（带完整参数）
                // END 由 done=true + done_reason=tool_calls 时发送
                return StreamEvent.builder()
                        .type(StreamEvent.Type.TOOL_CALL_START)
                        .toolName(name)
                        .content(arguments)
                        .arguments(argsMap)
                        .build();
            }

            // done=true 时判断是否需要发 TOOL_CALL_END
            boolean done = Boolean.TRUE.equals(chunk.get("done"));
            if (done) {
                String doneReason = (String) chunk.get("done_reason");
                if ("tool_calls".equals(normalizeFinishReason(doneReason))) {
                    return StreamEvent.builder()
                            .type(StreamEvent.Type.TOOL_CALL_END)
                            .build();
                }
                // done=true 无特殊原因，返回 null，由 doStream 末尾统一发 DONE
                return null;
            }

            return null;
        } catch (Exception e) {
            log.warn("[OllamaAdapter] 流式 chunk 解析失败: {}", rawChunk, e);
            return null;
        }
    }
}
