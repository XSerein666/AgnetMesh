package com.agentmesh.core.llm.adapter;

import com.agentmesh.core.llm.LlmChatResponse;
import com.agentmesh.core.llm.ToolCallRequest;
import com.agentmesh.core.llm.ToolChoice;
import com.agentmesh.core.llm.ToolDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
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
        if (rawFinishReason == null) return "stop";
        return switch (rawFinishReason) {
            case "stop" -> "stop";
            case "tool_calls", "function_call" -> "tool_calls";
            case "length" -> "length";
            default -> "stop";
        };
    }
}