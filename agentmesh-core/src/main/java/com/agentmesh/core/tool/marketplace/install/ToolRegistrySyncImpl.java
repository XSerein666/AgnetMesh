package com.agentmesh.core.tool.marketplace.install;

import com.agentmesh.core.tool.Tool;
import com.agentmesh.core.tool.ToolRegistry;
import com.agentmesh.core.tool.marketplace.execution.RemoteToolProxy;
import com.agentmesh.core.tool.marketplace.execution.ToolExecutionBridge;
import com.agentmesh.core.tool.marketplace.model.ToolExecutionDescriptor;
import com.agentmesh.core.tool.marketplace.model.ToolMetadata;
import com.agentmesh.core.tool.marketplace.model.ToolVersion;
import com.agentmesh.core.tool.marketplace.service.ToolMarketplace;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;

import java.util.List;
import java.util.Map;

/**
 * 工具注册表同步实现。
 */
@Slf4j
public class ToolRegistrySyncImpl implements ToolRegistrySync {

    private final ToolRegistry toolRegistry;
    private final ToolInstallManager installManager;
    private final ToolMarketplace marketplace;
    private final ToolExecutionBridge executionBridge;
    private final ApplicationContext applicationContext;

    public ToolRegistrySyncImpl(ToolRegistry toolRegistry,
                                 ToolInstallManager installManager,
                                 ToolMarketplace marketplace,
                                 ToolExecutionBridge executionBridge,
                                 ApplicationContext applicationContext) {
        this.toolRegistry = toolRegistry;
        this.installManager = installManager;
        this.marketplace = marketplace;
        this.executionBridge = executionBridge;
        this.applicationContext = applicationContext;
    }

    @Override
    public void syncOnStartup() {
        Map<String, ToolVersion> installed = installManager.getInstalledTools();
        for (Map.Entry<String, ToolVersion> entry : installed.entrySet()) {
            String toolId = entry.getKey();
            ToolVersion version = entry.getValue();
            try {
                ToolMetadata metadata = marketplace.getDetail(toolId, version);
                if (metadata != null) {
                    ToolExecutionDescriptor descriptor = metadata.getExecutionDescriptor();
                    if (descriptor != null && descriptor.getType() == ToolExecutionDescriptor.ExecutionType.LOCAL_BEAN) {
                        Object bean = applicationContext.getBean(descriptor.getBeanName());
                        if (bean instanceof Tool<?, ?> tool) {
                            toolRegistry.register(tool);
                        }
                    } else {
                        Tool<?, ?> proxy = new RemoteToolProxy(
                                metadata.getToolId(), metadata.getName(), metadata.getDescription(),
                                metadata.getInputSchema(), metadata.getExecutionDescriptor(), executionBridge);
                        toolRegistry.register(proxy);
                    }
                    log.info("[ToolRegistrySync] 启动同步: {} v{}", toolId, version);
                }
            } catch (Exception e) {
                log.error("[ToolRegistrySync] 启动同步失败: {} v{}", toolId, version, e);
            }
        }
    }

    @Override
    public void syncAfterInstall(String toolId, ToolVersion version) {
        log.info("[ToolRegistrySync] 安装后同步: {} v{}", toolId, version);
    }

    @Override
    public void syncAfterUninstall(String toolId) {
        toolRegistry.unregister(toolId);
        log.info("[ToolRegistrySync] 卸载后同步: {}", toolId);
    }

    @Override
    public void syncFromRemoteAgent(String agentId, String endpointUrl) {
        List<ToolMetadata> remoteTools = fetchRemoteTools(endpointUrl);
        for (ToolMetadata metadata : remoteTools) {
            ToolExecutionDescriptor remoteDescriptor = ToolExecutionDescriptor.builder()
                    .type(ToolExecutionDescriptor.ExecutionType.REMOTE_RPC)
                    .remoteEndpointUrl(endpointUrl + "/api/v1/internal/tools/" + metadata.getToolId() + "/execute")
                    .timeoutMillis(metadata.getExecutionDescriptor() != null
                            ? metadata.getExecutionDescriptor().getTimeoutMillis() : 30000)
                    .build();
            metadata.setExecutionDescriptor(remoteDescriptor);

            Tool<?, ?> proxy = new RemoteToolProxy(
                    metadata.getToolId(), metadata.getName(), metadata.getDescription(),
                    metadata.getInputSchema(), remoteDescriptor, executionBridge);
            toolRegistry.register(proxy);
            log.info("[ToolRegistrySync] 远程工具已注册: {} (来自 {})", metadata.getToolId(), agentId);
        }
    }

    private List<ToolMetadata> fetchRemoteTools(String endpointUrl) {
        return List.of();
    }
}
