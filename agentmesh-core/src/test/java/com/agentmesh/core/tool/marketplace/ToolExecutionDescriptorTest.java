package com.agentmesh.core.tool.marketplace;

import com.agentmesh.core.tool.marketplace.model.ToolExecutionDescriptor;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ToolExecutionDescriptorTest {

    @Test
    void shouldBuildLocalBeanDescriptor() {
        ToolExecutionDescriptor desc = ToolExecutionDescriptor.builder()
                .type(ToolExecutionDescriptor.ExecutionType.LOCAL_BEAN)
                .beanName("myToolBean")
                .build();

        assertEquals(ToolExecutionDescriptor.ExecutionType.LOCAL_BEAN, desc.getType());
        assertEquals("myToolBean", desc.getBeanName());
        assertEquals(30000, desc.getTimeoutMillis());
        assertEquals(0, desc.getRetryCount());
        assertEquals(ToolExecutionDescriptor.SerializationStrategy.JSON, desc.getInputSerialization());
        assertEquals(ToolExecutionDescriptor.SerializationStrategy.JSON, desc.getOutputSerialization());
    }

    @Test
    void shouldBuildRemoteRpcDescriptor() {
        ToolExecutionDescriptor desc = ToolExecutionDescriptor.builder()
                .type(ToolExecutionDescriptor.ExecutionType.REMOTE_RPC)
                .remoteEndpointUrl("http://agent:8080/api/tools/execute")
                .timeoutMillis(5000)
                .retryCount(3)
                .extraConfig(Map.of("Authorization", "Bearer token123"))
                .build();

        assertEquals(ToolExecutionDescriptor.ExecutionType.REMOTE_RPC, desc.getType());
        assertEquals("http://agent:8080/api/tools/execute", desc.getRemoteEndpointUrl());
        assertEquals(5000, desc.getTimeoutMillis());
        assertEquals(3, desc.getRetryCount());
        assertEquals("Bearer token123", desc.getExtraConfig().get("Authorization"));
    }

    @Test
    void shouldBuildMcpEndpointDescriptor() {
        ToolExecutionDescriptor desc = ToolExecutionDescriptor.builder()
                .type(ToolExecutionDescriptor.ExecutionType.MCP_ENDPOINT)
                .mcpServerUrl("http://mcp-server:8080")
                .mcpToolName("my-mcp-tool")
                .build();

        assertEquals(ToolExecutionDescriptor.ExecutionType.MCP_ENDPOINT, desc.getType());
        assertEquals("http://mcp-server:8080", desc.getMcpServerUrl());
        assertEquals("my-mcp-tool", desc.getMcpToolName());
    }
}