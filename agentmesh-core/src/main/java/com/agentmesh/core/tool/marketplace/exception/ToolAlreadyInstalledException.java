package com.agentmesh.core.tool.marketplace.exception;

/** 工具已安装 */
public class ToolAlreadyInstalledException extends ToolMarketplaceException {
    public ToolAlreadyInstalledException(String toolId) {
        super("工具已安装: " + toolId, toolId);
    }
}
