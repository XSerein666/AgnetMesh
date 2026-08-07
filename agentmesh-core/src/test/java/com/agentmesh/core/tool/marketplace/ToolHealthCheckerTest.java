package com.agentmesh.core.tool.marketplace;

import com.agentmesh.core.tool.marketplace.execution.ToolExecutionBridge;
import com.agentmesh.core.tool.marketplace.health.ToolHealthChecker;
import com.agentmesh.core.tool.marketplace.model.ToolExecutionDescriptor;
import com.agentmesh.core.tool.marketplace.model.ToolMetadata;
import com.agentmesh.core.tool.marketplace.model.ToolVersion;
import com.agentmesh.core.tool.marketplace.repository.ToolRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ToolHealthCheckerTest {

    @Mock
    private ToolRepository toolRepository;
    @Mock
    private ToolExecutionBridge executionBridge;

    private ToolHealthChecker healthChecker;

    @BeforeEach
    void setUp() {
        healthChecker = new ToolHealthChecker(toolRepository, executionBridge);
    }

    @Test
    void shouldReturnUnknownForNewTool() {
        assertEquals(ToolHealthChecker.HealthStatus.UNKNOWN, healthChecker.getStatus("unknown-tool"));
    }

    @Test
    void shouldCheckHealthOfRemoteTools() {
        ToolMetadata remote = ToolMetadata.builder()
                .toolId("pub:remote-tool").name("Remote Tool")
                .status(ToolMetadata.ToolStatus.PUBLISHED)
                .version(ToolVersion.parse("1.0.0"))
                .executionDescriptor(ToolExecutionDescriptor.builder()
                        .type(ToolExecutionDescriptor.ExecutionType.REMOTE_RPC)
                        .remoteEndpointUrl("http://remote:8080/api/tools")
                        .build())
                .build();
        when(toolRepository.findAllPublished()).thenReturn(List.of(remote));

        healthChecker.checkHealth();

        assertEquals(ToolHealthChecker.HealthStatus.HEALTHY, healthChecker.getStatus("pub:remote-tool"));
    }

    @Test
    void shouldMarkAgentOffline() {
        ToolMetadata tool1 = ToolMetadata.builder()
                .toolId("agent1:tool-a").name("Tool A").publisher("agent1")
                .status(ToolMetadata.ToolStatus.PUBLISHED).version(ToolVersion.parse("1.0.0"))
                .build();
        ToolMetadata tool2 = ToolMetadata.builder()
                .toolId("agent1:tool-b").name("Tool B").publisher("agent1")
                .status(ToolMetadata.ToolStatus.PUBLISHED).version(ToolVersion.parse("1.0.0"))
                .build();
        when(toolRepository.findByPublisher("agent1")).thenReturn(List.of(tool1, tool2));

        healthChecker.markAgentOffline("agent1");

        assertEquals(ToolHealthChecker.HealthStatus.UNHEALTHY, healthChecker.getStatus("agent1:tool-a"));
        assertEquals(ToolHealthChecker.HealthStatus.UNHEALTHY, healthChecker.getStatus("agent1:tool-b"));
    }

    @Test
    void shouldMarkAgentOnline() {
        ToolMetadata tool = ToolMetadata.builder()
                .toolId("agent1:tool-a").name("Tool A").publisher("agent1")
                .status(ToolMetadata.ToolStatus.PUBLISHED).version(ToolVersion.parse("1.0.0"))
                .build();
        when(toolRepository.findByPublisher("agent1")).thenReturn(List.of(tool));

        healthChecker.markAgentOffline("agent1");
        assertEquals(ToolHealthChecker.HealthStatus.UNHEALTHY, healthChecker.getStatus("agent1:tool-a"));

        healthChecker.markAgentOnline("agent1");
        assertEquals(ToolHealthChecker.HealthStatus.HEALTHY, healthChecker.getStatus("agent1:tool-a"));
    }
}