package com.agentmesh.core.tool.marketplace.exception;

/** 工具版本不存在 */
public class ToolVersionNotFoundException extends ToolMarketplaceException {
    private final String version;

    public ToolVersionNotFoundException(String toolId, String version) {
        super("工具版本不存在: " + toolId + " v" + version, toolId);
        this.version = version;
    }

    public String getVersion() {
        return version;
    }
}
