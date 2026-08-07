package com.agentmesh.core.tool.marketplace;

import com.agentmesh.core.tool.Tool;
import com.agentmesh.core.tool.ToolRegistry;
import com.agentmesh.core.tool.marketplace.exception.ToolAlreadyInstalledException;
import com.agentmesh.core.tool.marketplace.execution.ToolExecutionBridge;
import com.agentmesh.core.tool.marketplace.install.ToolInstallManager;
import com.agentmesh.core.tool.marketplace.model.ToolExecutionDescriptor;
import com.agentmesh.core.tool.marketplace.model.ToolMetadata;
import com.agentmesh.core.tool.marketplace.model.ToolVersion;
import com.agentmesh.core.tool.marketplace.service.ToolMarketplace;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ToolInstallManagerTest {

    @Mock
    private ToolRegistry toolRegistry;
    @Mock
    private ToolMarketplace marketplace;
    @Mock
    private ApplicationContext applicationContext;
    @Mock
    private ObjectProvider<ToolExecutionBridge> bridgeProvider;
    @Mock
    private Tool<?, ?> mockTool;

    private SimpleMeterRegistry meterRegistry;
    private ToolInstallManager installManager;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        lenient().when(bridgeProvider.getIfAvailable()).thenReturn(null);
        lenient().when(mockTool.getId()).thenReturn("pub:tool-a");

        installManager = new ToolInstallManager(
                toolRegistry, marketplace, applicationContext, bridgeProvider, meterRegistry);
    }

    private ToolMetadata createPublishedTool(String toolId, String name, ToolVersion version) {
        return ToolMetadata.builder()
                .toolId(toolId).name(name).description("desc")
                .category("DATA_PROCESSING").version(version)
                .status(ToolMetadata.ToolStatus.PUBLISHED)
                .executionDescriptor(ToolExecutionDescriptor.builder()
                        .type(ToolExecutionDescriptor.ExecutionType.LOCAL_BEAN)
                        .beanName(toolId).build())
                .build();
    }

    @Test
    void shouldInstallLocalBeanTool() {
        ToolVersion v1 = ToolVersion.parse("1.0.0");
        ToolMetadata meta = createPublishedTool("pub:tool-a", "Tool A", v1);
        when(marketplace.getDetail("pub:tool-a", v1)).thenReturn(meta);
        when(applicationContext.getBean("pub:tool-a")).thenReturn(mockTool);

        ToolMetadata result = installManager.install("pub:tool-a", v1);

        assertNotNull(result);
        assertEquals("pub:tool-a", result.getToolId());
        verify(toolRegistry).register(mockTool);
        assertTrue(installManager.getInstalledTools().containsKey("pub:tool-a"));
    }

    @Test
    void shouldRejectDuplicateInstall() {
        ToolVersion v1 = ToolVersion.parse("1.0.0");
        ToolMetadata meta = createPublishedTool("pub:tool-a", "Tool A", v1);
        when(marketplace.getDetail("pub:tool-a", v1)).thenReturn(meta);
        when(applicationContext.getBean("pub:tool-a")).thenReturn(mockTool);

        installManager.install("pub:tool-a", v1);

        assertThrows(ToolAlreadyInstalledException.class,
                () -> installManager.install("pub:tool-a", v1));
    }

    @Test
    void shouldRejectInstallOfNonPublishedTool() {
        ToolVersion v1 = ToolVersion.parse("1.0.0");
        ToolMetadata meta = ToolMetadata.builder()
                .toolId("pub:tool-a").name("Tool A").version(v1)
                .status(ToolMetadata.ToolStatus.PENDING_REVIEW).build();
        when(marketplace.getDetail("pub:tool-a", v1)).thenReturn(meta);

        assertThrows(IllegalArgumentException.class,
                () -> installManager.install("pub:tool-a", v1));
    }

    @Test
    void shouldUninstallTool() {
        ToolVersion v1 = ToolVersion.parse("1.0.0");
        ToolMetadata meta = createPublishedTool("pub:tool-a", "Tool A", v1);
        when(marketplace.getDetail("pub:tool-a", v1)).thenReturn(meta);
        when(applicationContext.getBean("pub:tool-a")).thenReturn(mockTool);

        installManager.install("pub:tool-a", v1);
        installManager.uninstall("pub:tool-a");

        verify(toolRegistry).unregister("pub:tool-a");
        assertFalse(installManager.getInstalledTools().containsKey("pub:tool-a"));
    }

    @Test
    void shouldRejectUninstallWithDependents() {
        ToolVersion v1 = ToolVersion.parse("1.0.0");
        ToolMetadata metaA = createPublishedTool("pub:tool-a", "Tool A", v1);
        when(marketplace.getDetail("pub:tool-a", v1)).thenReturn(metaA);
        when(applicationContext.getBean("pub:tool-a")).thenReturn(mockTool);

        Tool<?, ?> mockToolB = mock(Tool.class);
        ToolMetadata metaB = ToolMetadata.builder()
                .toolId("pub:tool-b").name("Tool B").description("desc")
                .category("DATA_PROCESSING").version(v1)
                .status(ToolMetadata.ToolStatus.PUBLISHED)
                .dependencies(List.of("pub:tool-a"))
                .executionDescriptor(ToolExecutionDescriptor.builder()
                        .type(ToolExecutionDescriptor.ExecutionType.LOCAL_BEAN)
                        .beanName("pub:tool-b").build())
                .build();
        when(marketplace.getDetail("pub:tool-b", v1)).thenReturn(metaB);
        when(applicationContext.getBean("pub:tool-b")).thenReturn(mockToolB);

        installManager.install("pub:tool-a", v1);
        installManager.install("pub:tool-b", v1);

        assertThrows(IllegalStateException.class,
                () -> installManager.uninstall("pub:tool-a"));
    }

    @Test
    void shouldUpgradeTool() {
        ToolVersion v1 = ToolVersion.parse("1.0.0");
        ToolVersion v2 = ToolVersion.parse("2.0.0");
        ToolMetadata metaV1 = createPublishedTool("pub:tool-a", "Tool A", v1);
        ToolMetadata metaV2 = createPublishedTool("pub:tool-a", "Tool A v2", v2);

        when(marketplace.getDetail("pub:tool-a", v1)).thenReturn(metaV1);
        when(applicationContext.getBean("pub:tool-a")).thenReturn(mockTool);

        installManager.install("pub:tool-a", v1);

        Tool<?, ?> mockToolV2 = mock(Tool.class);
        when(marketplace.getDetail("pub:tool-a", v2)).thenReturn(metaV2);
        when(applicationContext.getBean("pub:tool-a")).thenReturn(mockToolV2);

        ToolMetadata result = installManager.upgrade("pub:tool-a", v2);

        assertEquals(v2, result.getVersion());
        assertEquals(v2, installManager.getInstalledTools().get("pub:tool-a"));
    }

    @Test
    void shouldRejectUpgradeOfNonInstalledTool() {
        assertThrows(IllegalStateException.class,
                () -> installManager.upgrade("pub:tool-a", ToolVersion.parse("2.0.0")));
    }

    @Test
    void shouldAutoInstallDependencies() {
        ToolVersion v1 = ToolVersion.parse("1.0.0");
        ToolMetadata dep = createPublishedTool("pub:dep-a", "Dep A", v1);
        when(marketplace.getDetail("pub:dep-a", v1)).thenReturn(dep);
        lenient().when(marketplace.getDetail("pub:dep-a")).thenReturn(dep);
        when(applicationContext.getBean("pub:dep-a")).thenReturn(mockTool);

        Tool<?, ?> mockToolB = mock(Tool.class);
        ToolMetadata metaB = ToolMetadata.builder()
                .toolId("pub:tool-b").name("Tool B").description("desc")
                .category("DATA_PROCESSING").version(v1)
                .status(ToolMetadata.ToolStatus.PUBLISHED)
                .dependencies(List.of("pub:dep-a"))
                .executionDescriptor(ToolExecutionDescriptor.builder()
                        .type(ToolExecutionDescriptor.ExecutionType.LOCAL_BEAN)
                        .beanName("pub:tool-b").build())
                .build();
        when(marketplace.getDetail("pub:tool-b", v1)).thenReturn(metaB);
        when(applicationContext.getBean("pub:tool-b")).thenReturn(mockToolB);

        ToolMetadata result = installManager.install("pub:tool-b", v1);

        assertNotNull(result);
        assertTrue(installManager.getInstalledTools().containsKey("pub:dep-a"));
        assertTrue(installManager.getInstalledTools().containsKey("pub:tool-b"));
    }

    @Test
    void shouldDetectCyclicDependency() {
        ToolVersion v1 = ToolVersion.parse("1.0.0");
        Tool<?, ?> mockToolA = mock(Tool.class);
        lenient().when(mockToolA.getId()).thenReturn("pub:tool-a");
        ToolMetadata metaA = ToolMetadata.builder()
                .toolId("pub:tool-a").name("Tool A").description("desc")
                .category("DATA_PROCESSING").version(v1)
                .status(ToolMetadata.ToolStatus.PUBLISHED)
                .dependencies(List.of("pub:tool-b"))
                .executionDescriptor(ToolExecutionDescriptor.builder()
                        .type(ToolExecutionDescriptor.ExecutionType.LOCAL_BEAN)
                        .beanName("pub:tool-a").build())
                .build();

        ToolMetadata metaB = ToolMetadata.builder()
                .toolId("pub:tool-b").name("Tool B").description("desc")
                .category("DATA_PROCESSING").version(v1)
                .status(ToolMetadata.ToolStatus.PUBLISHED)
                .dependencies(List.of("pub:tool-a"))
                .executionDescriptor(ToolExecutionDescriptor.builder()
                        .type(ToolExecutionDescriptor.ExecutionType.LOCAL_BEAN)
                        .beanName("pub:tool-b").build())
                .build();

        lenient().when(marketplace.getDetail("pub:tool-a", v1)).thenReturn(metaA);
        lenient().when(marketplace.getDetail("pub:tool-b", v1)).thenReturn(metaB);
        lenient().when(marketplace.getDetail("pub:tool-b")).thenReturn(metaB);
        lenient().when(marketplace.getDetail("pub:tool-a")).thenReturn(metaA);

        assertThrows(IllegalStateException.class,
                () -> installManager.install("pub:tool-a", v1));
    }

    @Test
    void shouldCheckUpdates() {
        ToolVersion v1 = ToolVersion.parse("1.0.0");
        ToolVersion v2 = ToolVersion.parse("2.0.0");
        ToolMetadata metaV1 = createPublishedTool("pub:tool-a", "Tool A", v1);
        when(marketplace.getDetail("pub:tool-a", v1)).thenReturn(metaV1);
        when(applicationContext.getBean("pub:tool-a")).thenReturn(mockTool);

        installManager.install("pub:tool-a", v1);

        ToolMetadata metaV2 = createPublishedTool("pub:tool-a", "Tool A v2", v2);
        when(marketplace.getDetail("pub:tool-a")).thenReturn(metaV2);

        List<ToolMetadata> updates = installManager.checkUpdates();
        assertEquals(1, updates.size());
        assertEquals(v2, updates.get(0).getVersion());
    }

    @Test
    void shouldReturnEmptyUpdatesWhenLatest() {
        ToolVersion v2 = ToolVersion.parse("2.0.0");
        ToolMetadata meta = createPublishedTool("pub:tool-a", "Tool A", v2);
        when(marketplace.getDetail("pub:tool-a", v2)).thenReturn(meta);
        when(applicationContext.getBean("pub:tool-a")).thenReturn(mockTool);

        installManager.install("pub:tool-a", v2);

        when(marketplace.getDetail("pub:tool-a")).thenReturn(meta);
        assertTrue(installManager.checkUpdates().isEmpty());
    }

    @Test
    void shouldGetInstalledTools() {
        ToolVersion v1 = ToolVersion.parse("1.0.0");
        ToolMetadata meta = createPublishedTool("pub:tool-a", "Tool A", v1);
        when(marketplace.getDetail("pub:tool-a", v1)).thenReturn(meta);
        when(applicationContext.getBean("pub:tool-a")).thenReturn(mockTool);

        installManager.install("pub:tool-a", v1);

        Map<String, ToolVersion> installed = installManager.getInstalledTools();
        assertEquals(1, installed.size());
        assertEquals(v1, installed.get("pub:tool-a"));
    }
}