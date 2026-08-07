package com.agentmesh.core.tool.marketplace.config;

import com.agentmesh.core.tool.marketplace.health.CategoryRegistry;
import com.agentmesh.core.tool.marketplace.model.MarketplaceProperties;
import com.agentmesh.core.tool.marketplace.repository.InMemoryToolRepository;
import com.agentmesh.core.tool.marketplace.repository.JsonFileToolRepository;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "spring.agentmesh.marketplace.enabled", havingValue = "true")
public class ToolMarketplaceAutoConfiguration {

    @Bean
    @ConfigurationProperties(prefix = "spring.agentmesh.marketplace")
    public MarketplaceProperties marketplaceProperties() {
        return new MarketplaceProperties();
    }

    @Bean
    public CategoryRegistry categoryRegistry() {
        return new CategoryRegistry();
    }

    @Bean
    public InMemoryToolRepository inMemoryToolRepository() {
        return new InMemoryToolRepository();
    }

    @Bean
    public JsonFileToolRepository jsonFileToolRepository(
            InMemoryToolRepository delegate, MarketplaceProperties properties) {
        JsonFileToolRepository repo = new JsonFileToolRepository(delegate, properties.getDataDir());
        repo.load();
        return repo;
    }
}
