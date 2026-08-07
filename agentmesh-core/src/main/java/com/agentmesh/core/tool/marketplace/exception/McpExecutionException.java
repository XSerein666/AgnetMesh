package com.agentmesh.core.tool.marketplace.exception;

import com.agentmesh.core.tool.marketplace.model.ToolExecutionDescriptor;

/** MCP 协议执行异常 */
public class McpExecutionException extends ToolExecutionException {
    private final String mcpServerUrl;

    public McpExecutionException(String toolId, String mcpServerUrl, String message, Throwable cause) {
        super(toolId, ToolExecutionDescriptor.ExecutionType.MCP_ENDPOINT, message, cause);
        this.mcpServerUrl = mcpServerUrl;
    }

    public String getMcpServerUrl() {
        return mcpServerUrl;
    }
}
