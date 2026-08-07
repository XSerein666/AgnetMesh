package com.agentmesh.core.memory;

import com.agentmesh.core.llm.LlmClient;
import com.agentmesh.core.session.ConversationStore;
import com.agentmesh.core.session.InMemoryConversationStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 记忆模块自动配置。
 * <p>
 * 默认装配：
 * - InMemoryConversationStore（业务方可覆盖）
 * - SlidingWindowMemoryManager（用于短期记忆压缩）
 * <p>
 * 长期记忆（agentmesh.memory.long-term.enabled=true）：
 * - InMemoryVectorStore（默认实现，可被 PgVectorStore 等覆盖）
 * - MemoryExtractor（用于从对话中提取长期记忆）
 * <p>
 * 若业务方未提供 LlmClient，summaryLlmClient 注入会失败 → MemoryManager 也不装配。
 */
@AutoConfiguration
@EnableConfigurationProperties(MemoryProperties.class)
public class MemoryAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ConversationStore conversationStore() {
        return new InMemoryConversationStore();
    }

    @Bean
    @ConditionalOnMissingBean
    public MemoryManager memoryManager(ConversationStore conversationStore,
                                         LlmClient summaryLlmClient,
                                         MemoryProperties properties,
                                         ObjectProvider<VectorStore> vectorStoreProvider,
                                         ObjectProvider<MemoryExtractor> memoryExtractorProvider) {
        return new SlidingWindowMemoryManager(conversationStore, summaryLlmClient,
                properties, vectorStoreProvider.getIfAvailable(),
                memoryExtractorProvider.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "agentmesh.memory.long-term.enabled", havingValue = "true")
    public VectorStore vectorStore() {
        return new InMemoryVectorStore();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "agentmesh.memory.long-term.enabled", havingValue = "true")
    public MemoryExtractor memoryExtractor(LlmClient llmClient, MemoryProperties properties) {
        return new MemoryExtractor(llmClient,
                properties.getLongTerm().getExtraction().getMaxItemsPerTurn());
    }
}
