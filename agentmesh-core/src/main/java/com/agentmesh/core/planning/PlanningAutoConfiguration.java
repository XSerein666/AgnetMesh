package com.agentmesh.core.planning;

import com.agentmesh.core.llm.LlmClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.time.Duration;

/**
 * 任务规划模块自动配置。
 * <p>
 * 默认关闭，启用方式：agentmesh.planning.enabled=true。
 * <p>
 * 装配 Bean：
 * - LlmTaskPlanner：用 LLM 将目标分解为子任务 DAG
 * - DagPlanExecutor：基于线程池的 DAG 拓扑执行器
 * <p>
 * 若业务方未提供 LlmClient，planner 装配会失败 → Planning 模块不生效。
 */
@AutoConfiguration
@EnableConfigurationProperties(PlanningProperties.class)
@ConditionalOnProperty(name = "agentmesh.planning.enabled", havingValue = "true")
public class PlanningAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public TaskPlanner taskPlanner(LlmClient llmClient, PlanningProperties properties) {
        return new LlmTaskPlanner(llmClient, properties.getMaxSubtasks());
    }

    @Bean
    @ConditionalOnMissingBean
    public PlanExecutor planExecutor(PlanningProperties properties) {
        return new DagPlanExecutor(4,
                Duration.ofSeconds(properties.getTimeout()),
                properties.isParallel());
    }
}
