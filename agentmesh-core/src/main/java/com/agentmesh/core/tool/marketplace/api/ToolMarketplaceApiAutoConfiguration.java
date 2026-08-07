package com.agentmesh.core.tool.marketplace.api;

import com.agentmesh.core.tool.marketplace.health.CategoryRegistry;
import com.agentmesh.core.tool.marketplace.health.ToolHealthChecker;
import com.agentmesh.core.tool.marketplace.install.ToolInstallManager;
import com.agentmesh.core.tool.marketplace.service.ToolMarketplace;
import com.agentmesh.core.tool.marketplace.service.ToolRecommendService;
import com.agentmesh.core.tool.marketplace.service.ToolSearchService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 市场门户 API 自动配置。
 * 注册 Controller 和权限拦截器。
 */
@Configuration
@ConditionalOnExpression(
    "${spring.agentmesh.marketplace.enabled:true} && ${spring.agentmesh.marketplace.phase4.enabled:false}")
public class ToolMarketplaceApiAutoConfiguration implements WebMvcConfigurer {

    private final MarketplacePermissionEvaluator permissionEvaluator;

    public ToolMarketplaceApiAutoConfiguration(MarketplacePermissionEvaluator permissionEvaluator) {
        this.permissionEvaluator = permissionEvaluator;
    }

    @Bean
    public ToolMarketplaceController toolMarketplaceController(
            ToolMarketplace marketplace,
            ToolSearchService searchService,
            ToolInstallManager installManager,
            ToolHealthChecker healthChecker,
            ToolRecommendService recommendService,
            CategoryRegistry categoryRegistry) {
        return new ToolMarketplaceController(marketplace, searchService, installManager,
                healthChecker, recommendService, categoryRegistry);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new MarketplaceAuthInterceptor(permissionEvaluator))
                .addPathPatterns("/api/v1/tool-marketplace/**");
    }
}
