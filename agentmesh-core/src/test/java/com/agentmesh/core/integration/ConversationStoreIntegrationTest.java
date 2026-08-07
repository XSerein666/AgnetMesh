package com.agentmesh.core.integration;

import com.agentmesh.core.session.ChatMessage;
import com.agentmesh.core.session.ConversationStore;
import com.agentmesh.core.session.InMemoryConversationStore;
import com.agentmesh.core.session.JdbcConversationStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 会话存储集成测试：验证 InMemoryConversationStore 和 JdbcConversationStore 的完整功能。
 */
@DisplayName("会话存储集成测试")
class ConversationStoreIntegrationTest {

    // ========== InMemoryConversationStore 测试 ==========

    @Nested
    @DisplayName("InMemoryConversationStore")
    class InMemoryTests {

        private final InMemoryConversationStore store = new InMemoryConversationStore(10);

        @Test
        @DisplayName("追加消息并获取历史")
        void shouldAppendAndGetHistory() {
            store.append("session-1", ChatMessage.builder()
                    .role("user").content("你好").build());
            store.append("session-1", ChatMessage.builder()
                    .role("assistant").content("你好！").build());

            List<ChatMessage> history = store.getHistory("session-1");

            assertThat(history).hasSize(2);
            assertThat(history.get(0).getRole()).isEqualTo("user");
            assertThat(history.get(1).getRole()).isEqualTo("assistant");
        }

        @Test
        @DisplayName("空会话返回空列表")
        void shouldReturnEmptyForUnknownSession() {
            List<ChatMessage> history = store.getHistory("non-existent");
            assertThat(history).isEmpty();
        }

        @Test
        @DisplayName("清除会话")
        void shouldClearSession() {
            store.append("session-1", ChatMessage.builder()
                    .role("user").content("测试").build());

            store.clear("session-1");

            assertThat(store.getHistory("session-1")).isEmpty();
        }

        @Test
        @DisplayName("超过 maxHistory 限制时移除最早消息")
        void shouldEvictOldestWhenExceedingMaxHistory() {
            // maxHistory = 10，追加 15 条消息
            for (int i = 0; i < 15; i++) {
                store.append("session-1", ChatMessage.builder()
                        .role("user").content("消息" + i).build());
            }

            List<ChatMessage> history = store.getHistory("session-1");

            assertThat(history).hasSize(10);
            // 最早的消息（0-4）已被移除，保留的是 5-14
            assertThat(history.get(0).getContent()).isEqualTo("消息5");
            assertThat(history.get(9).getContent()).isEqualTo("消息14");
        }

        @Test
        @DisplayName("多会话隔离")
        void shouldIsolateMultipleSessions() {
            store.append("session-1", ChatMessage.builder()
                    .role("user").content("A").build());
            store.append("session-2", ChatMessage.builder()
                    .role("user").content("B").build());

            assertThat(store.getHistory("session-1")).hasSize(1);
            assertThat(store.getHistory("session-2")).hasSize(1);
            assertThat(store.getHistory("session-1").get(0).getContent()).isEqualTo("A");
            assertThat(store.getHistory("session-2").get(0).getContent()).isEqualTo("B");
        }

        @Test
        @DisplayName("并发写入：多线程同时追加消息不丢失")
        void shouldHandleConcurrentWrites() throws Exception {
            int threadCount = 4;
            int messagesPerThread = 25;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);

            for (int t = 0; t < threadCount; t++) {
                final int threadId = t;
                executor.submit(() -> {
                    try {
                        for (int i = 0; i < messagesPerThread; i++) {
                            store.append("session-1", ChatMessage.builder()
                                    .role("user")
                                    .content("t" + threadId + "-m" + i)
                                    .build());
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await();
            executor.shutdown();

            List<ChatMessage> history = store.getHistory("session-1");
            // maxHistory=10，所以最多保留 10 条
            assertThat(history).hasSize(10);
        }
    }

    // ========== JdbcConversationStore 测试 ==========

    @Nested
    @DisplayName("JdbcConversationStore")
    class JdbcTests {

        private final JdbcConversationStore store = new JdbcConversationStore();

        @Test
        @DisplayName("追加消息并获取历史")
        void shouldAppendAndGetHistory() {
            store.append("session-1", ChatMessage.builder()
                    .role("user").content("查询天气").build());
            store.append("session-1", ChatMessage.builder()
                    .role("assistant").content("北京晴").build());

            List<ChatMessage> history = store.getHistory("session-1");

            assertThat(history).hasSize(2);
            assertThat(history.get(0).getRole()).isEqualTo("user");
            assertThat(history.get(1).getContent()).isEqualTo("北京晴");
        }

        @Test
        @DisplayName("空会话返回空列表")
        void shouldReturnEmptyForUnknownSession() {
            assertThat(store.getHistory("no-session")).isEmpty();
        }

        @Test
        @DisplayName("清除会话")
        void shouldClearSession() {
            store.append("session-1", ChatMessage.builder()
                    .role("user").content("测试").build());

            store.clear("session-1");

            assertThat(store.getHistory("session-1")).isEmpty();
        }

        @Test
        @DisplayName("多会话隔离")
        void shouldIsolateMultipleSessions() {
            store.append("s1", ChatMessage.builder()
                    .role("user").content("A").build());
            store.append("s2", ChatMessage.builder()
                    .role("user").content("B").build());

            assertThat(store.getHistory("s1")).hasSize(1);
            assertThat(store.getHistory("s2")).hasSize(1);
        }

        @Test
        @DisplayName("并发写入：同一会话多线程追加不丢失消息")
        void shouldHandleConcurrentWrites() throws Exception {
            int threadCount = 4;
            int messagesPerThread = 25;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);

            for (int t = 0; t < threadCount; t++) {
                final int threadId = t;
                executor.submit(() -> {
                    try {
                        for (int i = 0; i < messagesPerThread; i++) {
                            store.append("session-1", ChatMessage.builder()
                                    .role("user")
                                    .content("t" + threadId + "-m" + i)
                                    .build());
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await();
            executor.shutdown();

            List<ChatMessage> history = store.getHistory("session-1");
            // 4 线程 × 25 条 = 100 条，全部保留
            assertThat(history).hasSize(threadCount * messagesPerThread);
        }
    }
}