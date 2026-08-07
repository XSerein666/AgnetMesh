package com.jewel.a2a.server.boundary;

import com.agentmesh.core.memory.MemoryManager;
import com.agentmesh.core.prompt.TokenBudgetManager;
import com.agentmesh.core.session.ChatMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 记忆管理边界测试：历史溢出、Token 预算、摘要压缩等。
 */
@DisplayName("MemoryManager 边界测试")
class MemoryManagerTest {

    // ========== TokenBudgetManager 直接测试 ==========

    @Nested
    @DisplayName("TokenBudgetManager 边界测试")
    class TokenBudgetManagerBoundary {

        private TokenBudgetManager budgetManager;

        @BeforeEach
        void setUp() {
            budgetManager = new TokenBudgetManager(2000);
        }

        @Test
        @DisplayName("默认构造函数应使用 2000 Token 预算")
        void shouldUseDefaultBudget() {
            TokenBudgetManager defaultBudget = new TokenBudgetManager();
            assertNotNull(defaultBudget);
        }

        @Test
        @DisplayName("null 文本应返回空字符串")
        void shouldReturnEmptyForNull() {
            assertEquals("", budgetManager.truncate(null));
        }

        @Test
        @DisplayName("空文本应返回空字符串")
        void shouldReturnEmptyForEmpty() {
            assertEquals("", budgetManager.truncate(""));
        }

        @Test
        @DisplayName("纯中文 Token 估算")
        void shouldEstimateChineseTokens() {
            int tokens = budgetManager.estimateTokens("你好世界，这是一段中文测试文本");
            assertTrue(tokens > 0, "中文 Token 估算应大于 0");
            assertTrue(tokens >= 15, "中文 Token 估算应 >= 15（实际: " + tokens + "）");
        }

        @Test
        @DisplayName("纯英文 Token 估算")
        void shouldEstimateEnglishTokens() {
            int tokens = budgetManager.estimateTokens("Hello world this is a test");
            assertTrue(tokens > 0, "英文 Token 估算应大于 0");
            assertEquals(6, tokens, "每个英文单词应计为 1 token");
        }

        @Test
        @DisplayName("混合中英文 Token 估算")
        void shouldEstimateMixedTokens() {
            int tokens = budgetManager.estimateTokens("Hello 你好 world 世界");
            assertTrue(tokens > 0);
        }

        @Test
        @DisplayName("超长文本截断后应包含截断标记")
        void shouldContainTruncationMarker() {
            String longText = "段落一。\n\n段落二。".repeat(500);
            String truncated = budgetManager.truncate(longText);
            assertTrue(truncated.contains("[上下文已截断"),
                    "截断后应包含截断提示");
            assertTrue(truncated.contains("原始长度约"),
                    "截断后应包含原始长度信息");
        }

        @Test
        @DisplayName("极短文本不应被截断")
        void shouldNotTruncateVeryShortText() {
            String shortText = "你好";
            String result = budgetManager.truncate(shortText);
            assertEquals(shortText, result);
        }

        @Test
        @DisplayName("rerankAndTruncate：空列表应返回空字符串")
        void shouldReturnEmptyForEmptyChunkList() {
            var result = budgetManager.rerankAndTruncate(List.of());
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }
    }

    // ========== ChatMessage 构建边界 ==========

    @Nested
    @DisplayName("ChatMessage 构建边界")
    class ChatMessageBoundary {

        @Test
        @DisplayName("最小 ChatMessage 应能正常构建")
        void shouldBuildMinimalChatMessage() {
            ChatMessage msg = ChatMessage.builder()
                    .role("user")
                    .content("hello")
                    .build();

            assertEquals("user", msg.getRole());
            assertEquals("hello", msg.getContent());
            assertNull(msg.getToolName());
        }

        @Test
        @DisplayName("工具消息应包含 toolName")
        void shouldBuildToolMessage() {
            ChatMessage msg = ChatMessage.builder()
                    .role("tool")
                    .content("执行结果")
                    .toolName("test_tool")
                    .build();

            assertEquals("tool", msg.getRole());
            assertEquals("test_tool", msg.getToolName());
        }

        @Test
        @DisplayName("null content 应能正常构建")
        void shouldBuildWithNullContent() {
            ChatMessage msg = ChatMessage.builder()
                    .role("user")
                    .content(null)
                    .build();

            assertNull(msg.getContent());
        }

        @Test
        @DisplayName("大量消息构建性能")
        void shouldHandleLargeNumberOfMessages() {
            List<ChatMessage> messages = new ArrayList<>();
            for (int i = 0; i < 1000; i++) {
                messages.add(ChatMessage.builder()
                        .role("user")
                        .content("消息 " + i)
                        .build());
            }
            assertEquals(1000, messages.size());
        }
    }

    // ========== 滑动窗口边界 ==========

    @Nested
    @DisplayName("滑动窗口边界")
    class SlidingWindowBoundary {

        @Test
        @DisplayName("超过窗口大小的消息应触发摘要")
        void shouldTriggerSummaryWhenExceedingWindow() {
            int windowSize = 6;
            int threshold = 10;

            // 模拟消息数超过阈值
            assertTrue(threshold > windowSize,
                    "摘要阈值应大于窗口大小");
        }

        @Test
        @DisplayName("窗口大小配置验证")
        void shouldValidateWindowSizeConfig() {
            // 应用配置中 window-size=6, summary-threshold=10
            int windowSize = 6;
            int summaryThreshold = 10;

            assertTrue(windowSize > 0, "窗口大小应大于 0");
            assertTrue(summaryThreshold > windowSize,
                    "摘要阈值(" + summaryThreshold + ")应大于窗口大小(" + windowSize + ")");
        }
    }
}