package com.agentmesh.core.tool.marketplace.config;

import com.agentmesh.core.agent.AgentConfig;
import com.agentmesh.core.tool.marketplace.execution.McpToolExecutor;
import com.agentmesh.core.tool.marketplace.execution.RemoteToolExecutor;
import com.agentmesh.core.tool.marketplace.execution.ToolExecutionBridge;
import com.agentmesh.core.tool.marketplace.health.AutoApprovePolicy;
import com.agentmesh.core.tool.marketplace.health.CategoryRegistry;
import com.agentmesh.core.tool.marketplace.health.ToolHealthChecker;
import com.agentmesh.core.tool.marketplace.health.ToolReviewPolicy;
import com.agentmesh.core.tool.marketplace.repository.InMemoryReviewRepository;
import com.agentmesh.core.tool.marketplace.repository.ReviewRepository;
import com.agentmesh.core.tool.marketplace.repository.ToolRepository;
import com.agentmesh.core.tool.marketplace.service.SimpleToolRecommendService;
import com.agentmesh.core.tool.marketplace.service.ToolMarketplace;
import com.agentmesh.core.tool.marketplace.service.ToolMarketplaceImpl;
import com.agentmesh.core.tool.marketplace.service.ToolRecommendService;
import com.agentmesh.core.tool.marketplace.service.ToolSearchService;
import com.agentmesh.core.tool.marketplace.service.ToolSearchServiceImpl;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Phase 2 自动配置。
 * 使用 @ConditionalOnExpression 替代多个 @ConditionalOnProperty 组合条件。
 */
@Configuration
@EnableScheduling
@ConditionalOnExpression(
    "${spring.agentmesh.marketplace.enabled:true} && ${spring.agentmesh.marketplace.phase2.enabled:false}")
public class ToolMarketplacePhase2AutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ToolReviewPolicy toolReviewPolicy() {
        return new AutoApprovePolicy();
    }

    @Bean
    @ConditionalOnMissingBean
    public ReviewRepository reviewRepository() {
        return new InMemoryReviewRepository();
    }

    @Bean
    @ConditionalOnMissingBean
    public ToolMarketplace toolMarketplace(ToolRepository toolRepository,
                                            CategoryRegistry categoryRegistry,
                                            ToolReviewPolicy reviewPolicy,
                                            ReviewRepository reviewRepository,
                                            ApplicationContext applicationContext,
                                            AgentConfig agentConfig) {
        return new ToolMarketplaceImpl(toolRepository, categoryRegistry, reviewPolicy,
                reviewRepository, applicationContext, agentConfig);
    }

    @Bean
    @ConditionalOnMissingBean
    public ToolSearchService toolSearchService(ToolRepository toolRepository) {
        return new ToolSearchServiceImpl(toolRepository);
    }

    @Bean
    @ConditionalOnMissingBean
    public ToolRecommendService toolRecommendService(ToolMarketplace marketplace, ToolRepository toolRepository) {
        return new SimpleToolRecommendService(marketplace, toolRepository);
    }

    @Bean
    @ConditionalOnMissingBean
    public ToolExecutionBridge toolExecutionBridge(ApplicationContext applicationContext,
                                                     RemoteToolExecutor remoteToolExecutor,
                                                     McpToolExecutor mcpToolExecutor) {
        return new ToolExecutionBridge(applicationContext, remoteToolExecutor, mcpToolExecutor);
    }

    @Bean
    @ConditionalOnMissingBean
    public RemoteToolExecutor remoteToolExecutor() {
        return new RemoteToolExecutor();
    }

    @Bean
    @ConditionalOnMissingBean
    public McpToolExecutor mcpToolExecutor() {
        return new McpToolExecutor();
    }

    @Bean
    @ConditionalOnMissingBean
    public ToolHealthChecker toolHealthChecker(ToolRepository toolRepository,
                                                ToolExecutionBridge executionBridge) {
        return new ToolHealthChecker(toolRepository, executionBridge);
    }
}
