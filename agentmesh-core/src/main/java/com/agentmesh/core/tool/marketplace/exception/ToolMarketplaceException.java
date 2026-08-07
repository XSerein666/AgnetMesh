package com.agentmesh.core.tool.marketplace.exception;

/**
 * 工具市场基础异常。
 */
public class ToolMarketplaceException extends RuntimeException {
    private final String toolId;

    public ToolMarketplaceException(String message, String toolId) {
        super(message);
        this.toolId = toolId;
    }

    public ToolMarketplaceException(String message, String toolId, Throwable cause) {
        super(message, cause);
        this.toolId = toolId;
    }

    public String getToolId() {
        return toolId;
    }
}
