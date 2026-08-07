package com.agentmesh.core.llm.adapter;

import com.agentmesh.core.llm.StreamEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OllamaAdapter 流式 chunk 解析测试。
 * 验证：
 *   - 文本增量 → TEXT 事件
 *   - done=true + done_reason 解析 → TOOL_CALL_END
 *   - 无 tool_calls 流式增量（Ollama 一次性返回完整调用）
 */
class OllamaAdapterStreamTest {

    private final OllamaAdapter adapter = new OllamaAdapter();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void shouldAdaptTextChunk() throws Exception {
        String chunk = """
                {
                  "message": {"content": "Hello"},
                  "done": false
                }""";

        StreamEvent event = adapter.adaptStreamChunk(chunk, mapper);

        assertNotNull(event);
        assertEquals(StreamEvent.Type.TEXT, event.getType());
        assertEquals("Hello", event.getContent());
    }

    @Test
    void shouldAdaptToolCallStartWithCompleteArguments() throws Exception {
        String chunk = """
                {
                  "message": {
                    "tool_calls": [{
                      "function": {
                        "name": "search",
                        "arguments": "{\\"query\\":\\"test\\"}"
                      }
                    }]
                  },
                  "done": false
                }""";

        StreamEvent event = adapter.adaptStreamChunk(chunk, mapper);

        assertNotNull(event);
        assertEquals(StreamEvent.Type.TOOL_CALL_START, event.getType());
        assertEquals("search", event.getToolName());
        assertNotNull(event.getArguments());
        assertEquals("test", event.getArguments().get("query"));
    }

    @Test
    void shouldEmitToolCallEndOnDoneWithToolCallsReason() throws Exception {
        String chunk = """
                {
                  "message": {},
                  "done": true,
                  "done_reason": "tool_calls"
                }""";

        StreamEvent event = adapter.adaptStreamChunk(chunk, mapper);

        assertNotNull(event);
        assertEquals(StreamEvent.Type.TOOL_CALL_END, event.getType());
    }

    @Test
    void shouldReturnNullForDoneWithoutToolCalls() throws Exception {
        String chunk = """
                {
                  "message": {},
                  "done": true,
                  "done_reason": "stop"
                }""";

        StreamEvent event = adapter.adaptStreamChunk(chunk, mapper);
        assertNull(event);
    }

    @Test
    void shouldReturnNullForMalformedJson() {
        StreamEvent event = adapter.adaptStreamChunk("not-json", mapper);
        assertNull(event);
    }
}
