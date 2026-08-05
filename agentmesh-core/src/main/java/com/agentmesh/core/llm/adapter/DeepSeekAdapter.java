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
 * DeepSeek 格式适配器，处理 finish_reason 枚举值差异
 */
@Slf4j
public class DeepSeekAdapter implements ProviderAdapter {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String getName() {
        return "deepseek";
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
            log.error("[DeepSeekAdapter] 响应解析失败", e);
            return LlmChatResponse.builder()
                    .content("")
                    .finishReason("stop")
                    .build();
        }
    }

    @Override
    public String normalizeFinishReason(String rawFinishReason) {
        if (rawFinishReason == null) return "stop";
        // DeepSeek 可能使用不同的枚举值
        return switch (rawFinishReason) {
            case "stop" -> "stop";
            case "tool_calls", "function_call" -> "tool_calls";
            case "length", "max_tokens" -> "length";
            default -> "stop";
        };
    }
}