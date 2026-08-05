package com.agentmesh.core.memory;

import com.agentmesh.core.llm.LlmClient;
import com.agentmesh.core.llm.TokenEstimator;
import com.agentmesh.core.session.ChatMessage;
import com.agentmesh.core.session.InMemoryConversationStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SlidingWindowMemoryManagerTest {

    private InMemoryConversationStore conversationStore;
    private LlmClient summaryLlmClient;
    private MemoryProperties properties;
    private SlidingWindowMemoryManager memoryManager;

    @BeforeEach
    void setUp() {
        conversationStore = new InMemoryConversationStore();
        summaryLlmClient = mock(LlmClient.class);
        TokenEstimator estimator = text -> text.length();
        when(summaryLlmClient.getTokenEstimator()).thenReturn(estimator);
        when(summaryLlmClient.chat(any())).thenReturn("用户讨论了钻戒定制，偏好简约风格");

        properties = new MemoryProperties();
        properties.getShortTerm().setWindowSize(4);
        properties.getShortTerm().setSummaryThreshold(6);

        memoryManager = new SlidingWindowMemoryManager(conversationStore, summaryLlmClient, properties);
    }

    @Test
    void shouldReturnAllMessagesWhenBelowThreshold() {
        appendMessages(3);

        List<ChatMessage> result = memoryManager.recall("s1", 10000);

        assertEquals(3, result.size());
    }

    @Test
    void shouldCompressWhenAboveThreshold() {
        appendMessages(8);

        List<ChatMessage> result = memoryManager.recall("s1", 10000);

        // 摘要 + 窗口内消息（4条）
        assertTrue(result.size() >= 4);
        // 第一条应该是摘要
        assertEquals("system", result.get(0).getRole());
        assertTrue(result.get(0).getContent().contains("对话摘要"));
    }

    @Test
    void shouldDegradeWhenSummaryFails() {
        when(summaryLlmClient.chat(any())).thenThrow(new RuntimeException("LLM error"));

        appendMessages(10);
        List<ChatMessage> result = memoryManager.recall("s1", 10000);

        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @Test
    void shouldFitTokens() {
        appendMessages(20);

        // 设置很小的 Token 限制
        List<ChatMessage> result = memoryManager.recall("s1", 50);

        assertNotNull(result);
        // 结果不应超过限制
        int totalTokens = result.stream()
                .mapToInt(m -> m.getContent() != null ? m.getContent().length() : 0)
                .sum();
        assertTrue(totalTokens <= 50);
    }

    @Test
    void shouldReturnEmptyForEmptySession() {
        List<ChatMessage> result = memoryManager.recall("empty", 1000);
        assertTrue(result.isEmpty());
    }

    @Test
    void shouldForceCompress() {
        appendMessages(4); // 未达阈值
        memoryManager.compress("s1");

        String summary = memoryManager.getSummary("s1");
        assertNotNull(summary);
        assertFalse(summary.isEmpty());
    }

    @Test
    void shouldNotDuplicateSummary() {
        appendMessages(10);
        memoryManager.recall("s1", 10000);

        String first = memoryManager.getSummary("s1");
        memoryManager.recall("s1", 10000);
        String second = memoryManager.getSummary("s1");

        assertEquals(first, second); // 摘要不应重复生成
    }

    @Test
    void shouldReturnNoLongTermMemoryWhenNotConfigured() {
        String result = memoryManager.getLongTermMemory("s1", "query");
        assertEquals("", result);
    }

    private void appendMessages(int count) {
        for (int i = 0; i < count; i++) {
            memoryManager.append("s1", ChatMessage.builder()
                    .role(i % 2 == 0 ? "user" : "assistant")
                    .content("Message " + i)
                    .build());
        }
    }
}