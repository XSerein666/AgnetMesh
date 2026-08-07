package com.agentmesh.core.planning;

import com.agentmesh.core.llm.LlmClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Planning 自动装配测试。
 * 验证：
 *   1. Planning 默认关闭（不创建 TaskPlanner / PlanExecutor）
 *   2. enabled=true 时创建 LlmTaskPlanner + DagPlanExecutor
 */
class PlanningAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(PlanningAutoConfiguration.class))
            .withUserConfiguration(StubLlmClientConfig.class);

    @Test
    void disabledByDefault_shouldNotCreatePlanningBeans() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(TaskPlanner.class);
            assertThat(context).doesNotHaveBean(PlanExecutor.class);
        });
    }

    @Test
    void enabled_shouldCreatePlannerAndExecutor() {
        contextRunner
                .withPropertyValues("agentmesh.planning.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(TaskPlanner.class);
                    assertThat(context.getBean(TaskPlanner.class))
                            .isInstanceOf(LlmTaskPlanner.class);
                    assertThat(context).hasSingleBean(PlanExecutor.class);
                    assertThat(context.getBean(PlanExecutor.class))
                            .isInstanceOf(DagPlanExecutor.class);
                });
    }

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
