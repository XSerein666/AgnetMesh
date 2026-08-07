package com.agentmesh.core.tool.marketplace.exception;

/** 工具不存在 */
public class ToolNotFoundException extends ToolMarketplaceException {
    public ToolNotFoundException(String toolId) {
        super("工具不存在: " + toolId, toolId);
    }
}
