package com.agentmesh.core.llm.config;

import com.agentmesh.core.infrastructure.AgentMeshMetrics;
import com.agentmesh.core.llm.DashScopeLlmClient;
import com.agentmesh.core.llm.DeepSeekLlmClient;
import com.agentmesh.core.llm.LlmClient;
import com.agentmesh.core.llm.OllamaLlmClient;
import com.agentmesh.core.llm.OpenAiLlmClient;
import com.agentmesh.core.llm.adapter.DashScopeAdapter;
import com.agentmesh.core.llm.adapter.OllamaAdapter;
import com.agentmesh.core.llm.adapter.OpenAiAdapter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * LLM 自动配置
 * <p>
 * 通过 agentmesh.llm.provider 切换厂商：
 *   dashscope（默认）| openai | ollama | deepseek
 */
@AutoConfiguration
@EnableConfigurationProperties(LlmProperties.class)
public class LlmAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(LlmClient.class)
    @ConditionalOnProperty(name = "agentmesh.llm.provider", havingValue = "dashscope", matchIfMissing = true)
    public LlmClient dashScopeLlmClient(LlmProperties props, ObjectProvider<AgentMeshMetrics> metricsProvider) {
        var c = props.getDashscope();
        return new DashScopeLlmClient(c.getApiKey(), c.getTextModel(), c.getVisionModel(),
                new DashScopeAdapter(), metricsProvider.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean(LlmClient.class)
    @ConditionalOnProperty(name = "agentmesh.llm.provider", havingValue = "openai")
    public LlmClient openAiLlmClient(LlmProperties props, ObjectProvider<AgentMeshMetrics> metricsProvider) {
        var c = props.getOpenai();
        return new OpenAiLlmClient(c.getApiKey(), c.getBaseUrl(), c.getModel(),
                new OpenAiAdapter(), metricsProvider.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean(LlmClient.class)
    @ConditionalOnProperty(name = "agentmesh.llm.provider", havingValue = "ollama")
    public LlmClient ollamaLlmClient(LlmProperties props, ObjectProvider<AgentMeshMetrics> metricsProvider) {
        var c = props.getOllama();
        return new OllamaLlmClient(c.getBaseUrl(), c.getModel(),
                new OllamaAdapter(), metricsProvider.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean(LlmClient.class)
    @ConditionalOnProperty(name = "agentmesh.llm.provider", havingValue = "deepseek")
    public LlmClient deepSeekLlmClient(LlmProperties props, ObjectProvider<AgentMeshMetrics> metricsProvider) {
        var c = props.getDeepseek();
        return new DeepSeekLlmClient(c.getApiKey(), c.getBaseUrl(), c.getModel(),
                metricsProvider.getIfAvailable());
    }
}
