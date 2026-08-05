package com.agentmesh.core.llm.adapter;

import com.agentmesh.core.llm.LlmChatResponse;
import com.agentmesh.core.llm.ToolDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class OpenAiAdapterTest {

    private final OpenAiAdapter adapter = new OpenAiAdapter();

    @Test
    void testNormalToolCallsResponse() {
        String rawResponse = """
            {
                "choices": [{
                    "finish_reason": "tool_calls",
                    "message": {
                        "content": null,
                        "tool_calls": [{
                            "id": "call_123",
                            "function": {
                                "name": "weather",
                                "arguments": "{\\"city\\":\\"北京\\"}"
                            }
                        }]
                    }
                }]
            }""";

        LlmChatResponse response = adapter.adaptResponse(rawResponse);
        assertEquals("tool_calls", response.getFinishReason());
        assertNotNull(response.getToolCalls());
        assertEquals(1, response.getToolCalls().size());
        assertEquals("weather", response.getToolCalls().get(0).getName());
        assertEquals("北京", response.getToolCalls().get(0).getArguments().get("city"));
    }

    @Test
    void testEmptyToolsResponse() {
        String rawResponse = """
            {
                "choices": [{
                    "finish_reason": "stop",
                    "message": {
                        "content": "你好，有什么可以帮助你的？"
                    }
                }]
            }""";

        LlmChatResponse response = adapter.adaptResponse(rawResponse);
        assertEquals("stop", response.getFinishReason());
        assertEquals("你好，有什么可以帮助你的？", response.getContent());
        assertNull(response.getToolCalls());
    }

    @Test
    void testFinishReasonMapping() {
        assertEquals("stop", adapter.normalizeFinishReason("stop"));
        assertEquals("tool_calls", adapter.normalizeFinishReason("tool_calls"));
        assertEquals("tool_calls", adapter.normalizeFinishReason("function_call"));
        assertEquals("length", adapter.normalizeFinishReason("length"));
        assertEquals("stop", adapter.normalizeFinishReason("unknown"));
        assertEquals("stop", adapter.normalizeFinishReason(null));
    }

    @Test
    void testAdaptTools() {
        List<ToolDefinition> tools = List.of(
                ToolDefinition.builder()
                        .name("weather")
                        .description("查询天气")
                        .parameters(Map.of("type", "object"))
                        .build()
        );
        List<Map<String, Object>> result = adapter.adaptTools(tools);
        assertEquals(1, result.size());
        assertEquals("function", result.get(0).get("type"));
    }

    @Test
    void testErrorResponse() {
        String rawResponse = "invalid json";
        LlmChatResponse response = adapter.adaptResponse(rawResponse);
        assertEquals("stop", response.getFinishReason());
        assertEquals("", response.getContent());
    }
}