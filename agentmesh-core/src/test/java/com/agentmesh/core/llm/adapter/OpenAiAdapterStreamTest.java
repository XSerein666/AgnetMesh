package com.agentmesh.core.llm.adapter;

import com.agentmesh.core.llm.StreamEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OpenAiAdapter 流式 chunk 解析测试。
 * 验证 16b-6 实现的 adaptStreamChunk：
 *   - 文本增量 → TEXT 事件
 *   - 工具调用首段（带 name）→ TOOL_CALL_START
 *   - 工具调用参数增量（无 name，有 arguments）→ TOOL_CALL_ARGS
 *   - finish_reason=tool_calls → TOOL_CALL_END
 *   - 非法 JSON → null
 */
class OpenAiAdapterStreamTest {

    private final OpenAiAdapter adapter = new OpenAiAdapter();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void shouldAdaptTextChunk() throws Exception {
        String chunk = """
                {
                  "choices": [{
                    "delta": {"content": "Hello"},
                    "finish_reason": null
                  }]
                }""";

        StreamEvent event = adapter.adaptStreamChunk(chunk, mapper);

        assertNotNull(event);
        assertEquals(StreamEvent.Type.TEXT, event.getType());
        assertEquals("Hello", event.getContent());
    }

    @Test
    void shouldAdaptToolCallStart() throws Exception {
        String chunk = """
                {
                  "choices": [{
                    "delta": {
                      "tool_calls": [{
                        "index": 0,
                        "id": "call_abc",
                        "type": "function",
                        "function": {"name": "weather", "arguments": ""}
                      }]
                    },
                    "finish_reason": null
                  }]
                }""";

        StreamEvent event = adapter.adaptStreamChunk(chunk, mapper);

        assertNotNull(event);
        assertEquals(StreamEvent.Type.TOOL_CALL_START, event.getType());
        assertEquals("weather", event.getToolName());
        assertEquals("call_abc", event.getToolCallId());
    }

    @Test
    void shouldAdaptToolCallArgsIncrement() throws Exception {
        String chunk = """
                {
                  "choices": [{
                    "delta": {
                      "tool_calls": [{
                        "index": 0,
                        "function": {"arguments": "{\\"city\\":"}
                      }]
                    },
                    "finish_reason": null
                  }]
                }""";

        StreamEvent event = adapter.adaptStreamChunk(chunk, mapper);

        assertNotNull(event);
        assertEquals(StreamEvent.Type.TOOL_CALL_ARGS, event.getType());
        assertEquals("{\"city\":", event.getContent());
    }

    @Test
    void shouldAdaptToolCallEnd() throws Exception {
        String chunk = """
                {
                  "choices": [{
                    "delta": {},
                    "finish_reason": "tool_calls"
                  }]
                }""";

        StreamEvent event = adapter.adaptStreamChunk(chunk, mapper);

        assertNotNull(event);
        assertEquals(StreamEvent.Type.TOOL_CALL_END, event.getType());
    }

    @Test
    void shouldReturnNullForMalformedJson() {
        StreamEvent event = adapter.adaptStreamChunk("not-a-json", mapper);
        assertNull(event);
    }

    @Test
    void shouldReturnNullForEmptyChoices() throws Exception {
        String chunk = """
                {
                  "choices": []
                }""";

        StreamEvent event = adapter.adaptStreamChunk(chunk, mapper);
        assertNull(event);
    }
}
