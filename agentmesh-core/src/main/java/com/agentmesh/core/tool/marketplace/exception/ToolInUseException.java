package com.agentmesh.core.tool.marketplace.exception;

import java.util.Set;

/** 工具有依赖方，无法卸载 */
public class ToolInUseException extends ToolMarketplaceException {
    private final Set<String> dependents;

    public ToolInUseException(String toolId, Set<String> dependents) {
        super("工具被以下工具依赖，无法卸载: " + dependents, toolId);
        this.dependents = dependents;
    }

    public Set<String> getDependents() {
        return dependents;
    }
}
