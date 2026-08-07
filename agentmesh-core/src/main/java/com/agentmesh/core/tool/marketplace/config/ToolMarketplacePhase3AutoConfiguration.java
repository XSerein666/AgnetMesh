package com.agentmesh.core.tool.marketplace.config;

import com.agentmesh.core.agent.AgentConfig;
import com.agentmesh.core.collaboration.MessageBus;
import com.agentmesh.core.registry.AgentRegistry;
import com.agentmesh.core.tool.ToolRegistry;
import com.agentmesh.core.tool.marketplace.execution.ToolExecutionBridge;
import com.agentmesh.core.tool.marketplace.install.ToolInstallManager;
import com.agentmesh.core.tool.marketplace.install.ToolRegistrySync;
import com.agentmesh.core.tool.marketplace.install.ToolRegistrySyncImpl;
import com.agentmesh.core.tool.marketplace.repository.ToolRepository;
import com.agentmesh.core.tool.marketplace.service.ToolMarketplace;
import com.agentmesh.core.tool.marketplace.service.ToolSharingService;
import com.agentmesh.core.tool.marketplace.service.ToolSharingServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Phase 3 自动配置（跨 Agent 工具共享 + 安装管理）。
 * 依赖 Phase 2 的 ToolMarketplace 和 ToolExecutionBridge。
 */
@Configuration
@ConditionalOnExpression(
    "${spring.agentmesh.marketplace.enabled:true} && ${spring.agentmesh.marketplace.phase3.enabled:false}")
public class ToolMarketplacePhase3AutoConfiguration {

    @Bean
    @ConditionalOnBean(ToolExecutionBridge.class)
    public ToolInstallManager toolInstallManager(ToolRegistry toolRegistry,
                                                   ToolMarketplace marketplace,
                                                   ApplicationContext applicationContext,
                                                   ObjectProvider<ToolExecutionBridge> bridgeProvider,
                                                   MeterRegistry meterRegistry) {
        return new ToolInstallManager(toolRegistry, marketplace, applicationContext, bridgeProvider, meterRegistry);
    }

    @Bean
    @ConditionalOnBean({MessageBus.class, ToolInstallManager.class})
    public ToolSharingService toolSharingService(MessageBus messageBus,
                                                   ToolMarketplace marketplace,
                                                   ToolRepository toolRepository,
                                                   AgentRegistry agentRegistry,
                                                   AgentConfig agentConfig,
                                                   ObjectMapper objectMapper) {
        return new ToolSharingServiceImpl(messageBus, marketplace, toolRepository,
                agentRegistry, agentConfig, objectMapper);
    }

    @Bean
    @ConditionalOnBean({ToolRegistry.class, ToolInstallManager.class, ToolExecutionBridge.class})
    public ToolRegistrySync toolRegistrySync(ToolRegistry toolRegistry,
                                               ToolInstallManager installManager,
                                               ToolMarketplace marketplace,
                                               ToolExecutionBridge executionBridge,
                                               ApplicationContext applicationContext) {
        return new ToolRegistrySyncImpl(toolRegistry, installManager, marketplace, executionBridge, applicationContext);
    }
}
