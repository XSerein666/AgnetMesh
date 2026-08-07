package com.agentmesh.core.tool.marketplace;

import com.agentmesh.core.tool.Tool;
import com.agentmesh.core.tool.ToolRegistry;
import com.agentmesh.core.tool.marketplace.execution.RemoteToolProxy;
import com.agentmesh.core.tool.marketplace.execution.ToolExecutionBridge;
import com.agentmesh.core.tool.marketplace.install.ToolInstallManager;
import com.agentmesh.core.tool.marketplace.install.ToolRegistrySyncImpl;
import com.agentmesh.core.tool.marketplace.model.ToolExecutionDescriptor;
import com.agentmesh.core.tool.marketplace.model.ToolMetadata;
import com.agentmesh.core.tool.marketplace.model.ToolVersion;
import com.agentmesh.core.tool.marketplace.service.ToolMarketplace;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ToolRegistrySyncImplTest {

    @Mock
    private ToolRegistry toolRegistry;
    @Mock
    private ToolInstallManager installManager;
    @Mock
    private ToolMarketplace marketplace;
    @Mock
    private ToolExecutionBridge executionBridge;
    @Mock
    private ApplicationContext applicationContext;

    private ToolRegistrySyncImpl sync;

    @BeforeEach
    void setUp() {
        sync = new ToolRegistrySyncImpl(
                toolRegistry, installManager, marketplace, executionBridge, applicationContext);
    }

    @Test
    void shouldSyncOnStartupWithLocalBeanTools() {
        ToolMetadata meta = ToolMetadata.builder()
                .toolId("pub:tool-a").name("Tool A").version(ToolVersion.parse("1.0.0"))
                .status(ToolMetadata.ToolStatus.PUBLISHED)
                .executionDescriptor(ToolExecutionDescriptor.builder()
                        .type(ToolExecutionDescriptor.ExecutionType.LOCAL_BEAN)
                        .beanName("pub:tool-a").build())
                .build();
        Tool<?, ?> mockTool = mock(Tool.class);

        when(installManager.getInstalledTools()).thenReturn(
                Map.of("pub:tool-a", ToolVersion.parse("1.0.0")));
        when(marketplace.getDetail("pub:tool-a", ToolVersion.parse("1.0.0"))).thenReturn(meta);
        when(applicationContext.getBean("pub:tool-a")).thenReturn(mockTool);

        sync.syncOnStartup();

        verify(toolRegistry).register(mockTool);
    }

    @Test
    void shouldSyncOnStartupWithRemoteTools() {
        ToolMetadata meta = ToolMetadata.builder()
                .toolId("pub:remote-tool").name("Remote Tool").version(ToolVersion.parse("1.0.0"))
                .status(ToolMetadata.ToolStatus.PUBLISHED)
                .executionDescriptor(ToolExecutionDescriptor.builder()
                        .type(ToolExecutionDescriptor.ExecutionType.REMOTE_RPC)
                        .remoteEndpointUrl("http://remote:8080/api/tools")
                        .build())
                .build();

        when(installManager.getInstalledTools()).thenReturn(
                Map.of("pub:remote-tool", ToolVersion.parse("1.0.0")));
        when(marketplace.getDetail("pub:remote-tool", ToolVersion.parse("1.0.0"))).thenReturn(meta);

        sync.syncOnStartup();

        verify(toolRegistry).register(any(RemoteToolProxy.class));
    }

    @Test
    void shouldHandleStartupSyncFailure() {
        when(installManager.getInstalledTools()).thenReturn(
                Map.of("pub:tool-a", ToolVersion.parse("1.0.0")));
        when(marketplace.getDetail("pub:tool-a", ToolVersion.parse("1.0.0")))
                .thenThrow(new RuntimeException("DB error"));

        assertDoesNotThrow(() -> sync.syncOnStartup());
    }

    @Test
    void shouldSyncAfterInstall() {
        assertDoesNotThrow(() -> sync.syncAfterInstall("pub:tool-a", ToolVersion.parse("1.0.0")));
    }

    @Test
    void shouldSyncAfterUninstall() {
        sync.syncAfterUninstall("pub:tool-a");
        verify(toolRegistry).unregister("pub:tool-a");
    }
}