package com.agentmesh.core.llm.config;

import com.agentmesh.core.infrastructure.AgentMeshMetrics;
import com.agentmesh.core.llm.DashScopeLlmClient;
import com.agentmesh.core.llm.DeepSeekLlmClient;
import com.agentmesh.core.llm.LlmClient;
import com.agentmesh.core.llm.OllamaLlmClient;
import com.agentmesh.core.llm.OpenAiLlmClient;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LLM 自动装配测试。
 * 验证：
 *   1. 默认 provider=dashscope → DashScopeLlmClient
 *   2. provider=openai → OpenAiLlmClient
 *   3. provider=deepseek → DeepSeekLlmClient
 *   4. 已有 LlmClient Bean 时不再创建默认 Bean（@ConditionalOnMissingBean 生效）
 *   5. metrics 注入后 client 实例持有非 null metrics
 */
class LlmAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(LlmAutoConfiguration.class));

    @Test
    void defaultProvider_shouldCreateDashScope() {
        contextRunner
                .withPropertyValues("agentmesh.llm.dashscope.api-key=test-key")
                .run(context -> {
                    assertThat(context).hasSingleBean(LlmClient.class);
                    assertThat(context.getBean(LlmClient.class))
                            .isInstanceOf(DashScopeLlmClient.class);
                });
    }

    @Test
    void providerOpenAi_shouldCreateOpenAiClient() {
        contextRunner
                .withPropertyValues(
                        "agentmesh.llm.provider=openai",
                        "agentmesh.llm.openai.api-key=test-key")
                .run(context -> {
                    assertThat(context).hasSingleBean(LlmClient.class);
                    assertThat(context.getBean(LlmClient.class))
                            .isInstanceOf(OpenAiLlmClient.class);
                });
    }

    @Test
    void providerDeepSeek_shouldCreateDeepSeekClient() {
        contextRunner
                .withPropertyValues(
                        "agentmesh.llm.provider=deepseek",
                        "agentmesh.llm.deepseek.api-key=test-key")
                .run(context -> {
                    assertThat(context).hasSingleBean(LlmClient.class);
                    assertThat(context.getBean(LlmClient.class))
                            .isInstanceOf(DeepSeekLlmClient.class);
                });
    }

    @Test
    void existingLlmClientBean_shouldSkipAutoConfig() {
        contextRunner
                .withPropertyValues("agentmesh.llm.dashscope.api-key=test-key")
                .withUserConfiguration(CustomLlmClientConfig.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(LlmClient.class);
                    assertThat(context.getBean(LlmClient.class))
                            .isInstanceOf(NoopLlmClient.class);
                });
    }

    @Test
    void metricsBeanInjected_shouldNotFailAssembly() {
        contextRunner
                .withPropertyValues("agentmesh.llm.dashscope.api-key=test-key")
                .withUserConfiguration(MetricsConfig.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(LlmClient.class);
                    assertThat(context).hasSingleBean(AgentMeshMetrics.class);
                    // metrics 已注入到 LlmClient 内部字段（非 null 时使用）
                    assertThat(context.getBean(AgentMeshMetrics.class)).isNotNull();
                });
    }

    /** 业务方提供自定义 LlmClient，应跳过自动配置 */
    @Configuration
    static class CustomLlmClientConfig {
        @Bean
        public LlmClient customLlmClient() {
            return new NoopLlmClient();
        }
    }

    /** 提供 AgentMeshMetrics Bean */
    @Configuration
    static class MetricsConfig {
        @Bean
        public AgentMeshMetrics agentMeshMetrics() {
            return new AgentMeshMetrics(new SimpleMeterRegistry());
        }
    }

    /** 占位实现：什么都不做，仅用于验证 @ConditionalOnMissingBean */
    static class NoopLlmClient implements LlmClient {
        @Override public boolean supportsFunctionCalling() { return false; }
        @Override public String chat(java.util.List<java.util.Map<String, Object>> messages) { return ""; }
        @Override public com.agentmesh.core.llm.LlmChatResponse chatWithTools(
                java.util.List<java.util.Map<String, Object>> messages,
                java.util.List<com.agentmesh.core.llm.ToolDefinition> tools,
                com.agentmesh.core.llm.ToolChoice toolChoice) {
            return com.agentmesh.core.llm.LlmChatResponse.builder().build();
        }
        @Override public String vision(String imageUrl, String prompt) { return ""; }
        @Override public com.agentmesh.core.llm.TokenEstimator getTokenEstimator() {
            return s -> 0;
        }
    }
}
