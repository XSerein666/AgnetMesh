package com.agentmesh.core.llm.config;

import com.agentmesh.core.llm.DashScopeLlmClient;
import com.agentmesh.core.llm.LlmClient;
import com.agentmesh.core.llm.OllamaLlmClient;
import com.agentmesh.core.llm.OpenAiLlmClient;
import com.agentmesh.core.llm.adapter.DashScopeAdapter;
import com.agentmesh.core.llm.adapter.OllamaAdapter;
import com.agentmesh.core.llm.adapter.OpenAiAdapter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * LLM 自动配置
 */
@Configuration
@EnableConfigurationProperties(LlmProperties.class)
public class LlmAutoConfiguration {

    @Bean
    @ConditionalOnProperty(name = "agentmesh.llm.provider", havingValue = "dashscope", matchIfMissing = true)
    public LlmClient dashScopeLlmClient(LlmProperties props) {
        var c = props.getDashscope();
        return new DashScopeLlmClient(c.getApiKey(), c.getTextModel(), c.getVisionModel(),
                new DashScopeAdapter());
    }

    @Bean
    @ConditionalOnProperty(name = "agentmesh.llm.provider", havingValue = "openai")
    public LlmClient openAiLlmClient(LlmProperties props) {
        var c = props.getOpenai();
        return new OpenAiLlmClient(c.getApiKey(), c.getBaseUrl(), c.getModel(),
                new OpenAiAdapter());
    }

    @Bean
    @ConditionalOnProperty(name = "agentmesh.llm.provider", havingValue = "ollama")
    public LlmClient ollamaLlmClient(LlmProperties props) {
        var c = props.getOllama();
        return new OllamaLlmClient(c.getBaseUrl(), c.getModel(),
                new OllamaAdapter());
    }
}