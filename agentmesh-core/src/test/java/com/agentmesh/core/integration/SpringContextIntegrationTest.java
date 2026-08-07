package com.agentmesh.core.integration;

import com.agentmesh.core.infrastructure.AgentMeshMetrics;
import com.agentmesh.core.llm.LlmClient;
import com.agentmesh.core.llm.OpenAiLlmClient;
import com.agentmesh.core.llm.config.LlmAutoConfiguration;
import com.agentmesh.core.routing.KeywordRoutingStrategy;
import com.agentmesh.core.routing.RoutingAutoConfiguration;
import com.agentmesh.core.routing.RoutingProperties;
import com.agentmesh.core.routing.RoutingStrategy;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spring Context 集成测试：验证多个自动装配类协作加载。
 * 测试 LlmAutoConfiguration + RoutingAutoConfiguration 联合工作。
 */
@DisplayName("Spring Context 集成测试")
class SpringContextIntegrationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    LlmAutoConfiguration.class,
                    RoutingAutoConfiguration.class))
            .withUserConfiguration(MetricsConfig.class);

    @Test
    @DisplayName("LlmClient + RoutingStrategy 联合装配成功")
    void shouldLoadLlmAndRoutingTogether() {
        contextRunner
                .withPropertyValues(
                        "agentmesh.llm.provider=openai",
                        "agentmesh.llm.openai.api-key=test-key",
                        "agentmesh.llm.openai.base-url=http://localhost:8080",
                        "agentmesh.routing.keyword.enabled=true",
                        "agentmesh.routing.llm.enabled=false")
                .run(context -> {
                    // LlmClient 被装配
                    assertThat(context).hasSingleBean(LlmClient.class);
                    assertThat(context.getBean(LlmClient.class))
                            .isInstanceOf(OpenAiLlmClient.class);

                    // RoutingProperties 被装配
                    assertThat(context).hasSingleBean(RoutingProperties.class);

                    // routingStrategy 是主策略 Bean（默认 = keywordRoutingStrategy）
                    assertThat(context).hasBean("routingStrategy");
                    RoutingStrategy strategy = context.getBean("routingStrategy", RoutingStrategy.class);
                    assertThat(strategy).isInstanceOf(KeywordRoutingStrategy.class);
                });
    }

    @Test
    @DisplayName("LLM 路由启用时，routingStrategy 为 LlmRoutingStrategy")
    void shouldLoadLlmRoutingWhenEnabled() {
        contextRunner
                .withPropertyValues(
                        "agentmesh.llm.provider=openai",
                        "agentmesh.llm.openai.api-key=test-key",
                        "agentmesh.llm.openai.base-url=http://localhost:8080",
                        "agentmesh.routing.keyword.enabled=true",
                        "agentmesh.routing.llm.enabled=true",
                        "agentmesh.routing.strategy=llm")
                .run(context -> {
                    assertThat(context).hasBean("routingStrategy");
                    String routingClassName = context.getBean("routingStrategy", RoutingStrategy.class)
                            .getClass().getSimpleName();
                    assertThat(routingClassName).contains("Llm");
                });
    }

    @Test
    @DisplayName("LLM 路由禁用时，routingStrategy 回退到关键词路由")
    void shouldFallbackToKeywordWhenLlmDisabled() {
        contextRunner
                .withPropertyValues(
                        "agentmesh.llm.provider=openai",
                        "agentmesh.llm.openai.api-key=test-key",
                        "agentmesh.llm.openai.base-url=http://localhost:8080",
                        "agentmesh.routing.keyword.enabled=true",
                        "agentmesh.routing.llm.enabled=false")
                .run(context -> {
                    RoutingStrategy strategy = context.getBean("routingStrategy", RoutingStrategy.class);
                    assertThat(strategy).isInstanceOf(KeywordRoutingStrategy.class);
                });
    }

    @Configuration
    static class MetricsConfig {
        @Bean
        public AgentMeshMetrics agentMeshMetrics() {
            return new AgentMeshMetrics(new SimpleMeterRegistry());
        }
    }
}