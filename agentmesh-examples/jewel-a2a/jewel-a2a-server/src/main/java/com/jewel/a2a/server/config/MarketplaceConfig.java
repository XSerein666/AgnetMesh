package com.jewel.a2a.server.config;

import com.agentmesh.core.tool.marketplace.health.CategoryRegistry;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

/**
 * 珠宝行业工具市场分类注册。
 * 在 Tool Marketplace 中注册珠宝行业专属分类。
 */
@Slf4j
@Configuration
public class MarketplaceConfig {

    private final CategoryRegistry categoryRegistry;

    public MarketplaceConfig(CategoryRegistry categoryRegistry) {
        this.categoryRegistry = categoryRegistry;
    }

    @PostConstruct
    public void registerCategories() {
        categoryRegistry.register("JEWELRY_DESIGN", "珠宝设计", "珠宝设计图生成相关工具");
        categoryRegistry.register("JEWELRY_CRAFT", "珠宝工艺", "工艺校验与知识检索工具");
        categoryRegistry.register("JEWELRY_IMAGE", "图片分析", "珠宝图片分析与鉴定工具");
        log.info("[MarketplaceConfig] 珠宝行业分类注册完成，共 3 个分类");
    }
}