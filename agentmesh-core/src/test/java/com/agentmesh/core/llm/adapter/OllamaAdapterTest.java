package com.agentmesh.core.llm.adapter;

import com.agentmesh.core.llm.LlmChatResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OllamaAdapterTest {

    private final OllamaAdapter adapter = new OllamaAdapter();

    @Test
    void testNormalToolCallsResponse() {
        String rawResponse = """
            {
                "message": {
                    "content": null,
                    "tool_calls": [{
                        "function": {
                            "name": "weather",
                            "arguments": {"city": "北京"}
                        }
                    }]
                },
                "done_reason": "stop"
            }""";

        LlmChatResponse response = adapter.adaptResponse(rawResponse);
        assertEquals("stop", response.getFinishReason());
        assertNotNull(response.getToolCalls());
        assertEquals(1, response.getToolCalls().size());
        assertEquals("weather", response.getToolCalls().get(0).getName());
    }

    @Test
    void testTextResponse() {
        String rawResponse = """
            {
                "message": {
                    "content": "你好"
                },
                "done_reason": "stop"
            }""";

        LlmChatResponse response = adapter.adaptResponse(rawResponse);
        assertEquals("stop", response.getFinishReason());
        assertEquals("你好", response.getContent());
    }

    @Test
    void testEmptyMessageResponse() {
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
        assertEquals("stop", adapter.normalizeFinishReason(null));
    }
}