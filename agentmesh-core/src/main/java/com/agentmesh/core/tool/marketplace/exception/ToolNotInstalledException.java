package com.agentmesh.core.tool.marketplace.exception;

/** 工具未安装 */
public class ToolNotInstalledException extends ToolMarketplaceException {
    public ToolNotInstalledException(String toolId) {
        super("工具未安装: " + toolId, toolId);
    }
}
