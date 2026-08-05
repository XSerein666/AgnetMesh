package com.agentmesh.core.memory;

import com.agentmesh.core.llm.LlmClient;
import com.agentmesh.core.session.ChatMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MemoryExtractorTest {

    private LlmClient llmClient;
    private MemoryExtractor extractor;

    @BeforeEach
    void setUp() {
        llmClient = mock(LlmClient.class);
        extractor = new MemoryExtractor(llmClient, 3);
    }

    @Test
    void shouldExtractMemories() {
        when(llmClient.chat(any())).thenReturn(
                "[{\"type\":\"PREFERENCE\",\"content\":\"用户喜欢简约风格\"},"
                        + "{\"type\":\"FACT\",\"content\":\"用户预算5000元\"}]");

        List<MemoryItem> items = extractor.extract("s1", createHistory());

        assertEquals(2, items.size());
        assertEquals("PREFERENCE", items.get(0).getType());
        assertEquals("用户喜欢简约风格", items.get(0).getContent());
        assertEquals("FACT", items.get(1).getType());
    }

    @Test
    void shouldReturnEmptyOnInvalidJson() {
        when(llmClient.chat(any())).thenReturn("not a json");

        List<MemoryItem> items = extractor.extract("s1", createHistory());

        assertTrue(items.isEmpty());
    }

    @Test
    void shouldReturnEmptyOnLlmTimeout() {
        when(llmClient.chat(any())).thenThrow(new RuntimeException("timeout"));

        List<MemoryItem> items = extractor.extract("s1", createHistory());

        assertTrue(items.isEmpty());
    }

    @Test
    void shouldReturnEmptyForEmptyHistory() {
        List<MemoryItem> items = extractor.extract("s1", List.of());
        assertTrue(items.isEmpty());
    }

    @Test
    void shouldRespectMaxItemsPerTurn() {
        when(llmClient.chat(any())).thenReturn(
                "[{\"type\":\"PREFERENCE\",\"content\":\"item1\"},"
                        + "{\"type\":\"FACT\",\"content\":\"item2\"},"
                        + "{\"type\":\"CONTEXT\",\"content\":\"item3\"},"
                        + "{\"type\":\"FACT\",\"content\":\"item4\"}]");

        List<MemoryItem> items = extractor.extract("s1", createHistory());

        assertEquals(3, items.size());
    }

    private List<ChatMessage> createHistory() {
        return List.of(
                ChatMessage.builder().role("user").content("我想设计一款钻戒").build(),
                ChatMessage.builder().role("assistant").content("好的，请问您有什么偏好？").build(),
                ChatMessage.builder().role("user").content("我喜欢简约风格，预算5000元").build()
        );
    }
}