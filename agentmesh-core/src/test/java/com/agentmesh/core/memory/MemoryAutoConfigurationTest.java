package com.agentmesh.core.memory;

import com.agentmesh.core.llm.LlmClient;
import com.agentmesh.core.session.ConversationStore;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Memory 自动装配测试。
 * 验证：
 *   1. SlidingWindowMemoryManager 自动创建（需提供 LlmClient）
 *   2. 长期记忆默认关闭（VectorStore / MemoryExtractor 不创建）
 *   3. long-term.enabled=true 时 InMemoryVectorStore 条件创建
 */
class MemoryAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MemoryAutoConfiguration.class));

    @Test
    void shouldCreateMemoryManager_whenLlmClientProvided() {
        contextRunner
                .withUserConfiguration(StubLlmClientConfig.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(MemoryManager.class);
                    assertThat(context.getBean(MemoryManager.class))
                            .isInstanceOf(SlidingWindowMemoryManager.class);
                    assertThat(context).hasSingleBean(ConversationStore.class);
                });
    }

    @Test
    void longTermDisabledByDefault_shouldNotCreateVectorStore() {
        contextRunner
                .withUserConfiguration(StubLlmClientConfig.class)
                .run(context -> {
                    assertThat(context).doesNotHaveBean(VectorStore.class);
                    assertThat(context).doesNotHaveBean(MemoryExtractor.class);
                });
    }

    @Test
    void longTermEnabled_shouldCreateVectorStoreAndExtractor() {
        contextRunner
                .withUserConfiguration(StubLlmClientConfig.class)
                .withPropertyValues("agentmesh.memory.long-term.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(VectorStore.class);
                    assertThat(context.getBean(VectorStore.class))
                            .isInstanceOf(InMemoryVectorStore.class);
                    assertThat(context).hasSingleBean(MemoryExtractor.class);
                });
    }

    /** 提供一个 stub LlmClient 供 MemoryManager 装配使用 */
    @Configuration
    static class StubLlmClientConfig {
        @Bean
        public LlmClient llmClient() {
            return new StubLlmClient();
        }
    }

    static class StubLlmClient implements LlmClient {
        @Override public boolean supportsFunctionCalling() { return false; }
        @Override public String chat(java.util.List<java.util.Map<String, Object>> messages) { return ""; }
        @Override public com.agentmesh.core.llm.LlmChatResponse chatWithTools(
                java.util.List<java.util.Map<String, Object>> messages,
                java.util.List<com.agentmesh.core.llm.ToolDefinition> tools,
                com.agentmesh.core.llm.ToolChoice toolChoice) {
            return com.agentmesh.core.llm.LlmChatResponse.builder().build();
        }
    }
}
