package com.agentmesh.core.tool.marketplace.exception;

/** 工具升级失败 */
public class ToolUpgradeException extends ToolMarketplaceException {
    public ToolUpgradeException(String toolId, String message, Throwable cause) {
        super("工具升级失败: " + toolId + " - " + message, toolId, cause);
    }
}
