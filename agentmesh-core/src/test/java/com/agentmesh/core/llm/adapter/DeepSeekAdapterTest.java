package com.agentmesh.core.llm.adapter;

import com.agentmesh.core.llm.LlmChatResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DeepSeekAdapterTest {

    private final DeepSeekAdapter adapter = new DeepSeekAdapter();

    @Test
    void testNormalToolCallsResponse() {
        String rawResponse = """
            {
                "choices": [{
                    "finish_reason": "tool_calls",
                    "message": {
                        "content": null,
                        "tool_calls": [{
                            "id": "call_456",
                            "function": {
                                "name": "search",
                                "arguments": "{\\"query\\":\\"AI\\"}"
                            }
                        }]
                    }
                }]
            }""";

        LlmChatResponse response = adapter.adaptResponse(rawResponse);
        assertEquals("tool_calls", response.getFinishReason());
        assertNotNull(response.getToolCalls());
        assertEquals(1, response.getToolCalls().size());
        assertEquals("search", response.getToolCalls().get(0).getName());
    }

    @Test
    void testEmptyResponse() {
        String rawResponse = "{}";
        LlmChatResponse response = adapter.adaptResponse(rawResponse);
        assertEquals("stop", response.getFinishReason());
        assertEquals("", response.getContent());
    }

    @Test
    void testFinishReasonNormalization() {
        assertEquals("stop", adapter.normalizeFinishReason("stop"));
        assertEquals("tool_calls", adapter.normalizeFinishReason("tool_calls"));
        assertEquals("length", adapter.normalizeFinishReason("max_tokens"));
        assertEquals("stop", adapter.normalizeFinishReason("unknown"));
        assertEquals("stop", adapter.normalizeFinishReason(null));
    }
}