package com.agentmesh.core.llm.adapter;

import com.agentmesh.core.llm.LlmChatResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DashScopeAdapterTest {

    private final DashScopeAdapter adapter = new DashScopeAdapter();

    @Test
    void testNormalToolCallsResponse() {
        String rawResponse = """
            {
                "output": {
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
                }
            }""";

        LlmChatResponse response = adapter.adaptResponse(rawResponse);
        assertEquals("tool_calls", response.getFinishReason());
        assertNotNull(response.getToolCalls());
        assertEquals(1, response.getToolCalls().size());
        assertEquals("weather", response.getToolCalls().get(0).getName());
    }

    @Test
    void testTextFallbackResponse() {
        String rawResponse = """
            {
                "output": {
                    "text": "你好，今天天气不错"
                }
            }""";

        LlmChatResponse response = adapter.adaptResponse(rawResponse);
        assertEquals("stop", response.getFinishReason());
        assertEquals("你好，今天天气不错", response.getContent());
    }

    @Test
    void testEmptyOutputResponse() {
        String rawResponse = "{}";
        LlmChatResponse response = adapter.adaptResponse(rawResponse);
        assertEquals("stop", response.getFinishReason());
        assertEquals("", response.getContent());
    }

    @Test
    void testFinishReasonNormalization() {
        assertEquals("stop", adapter.normalizeFinishReason("stop"));
        assertEquals("tool_calls", adapter.normalizeFinishReason("tool_calls"));
        assertEquals("length", adapter.normalizeFinishReason("length"));
    }
}