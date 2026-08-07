package com.agentmesh.core.tool.marketplace.exception;

import java.util.Set;

/** 循环依赖 */
public class CyclicDependencyException extends ToolMarketplaceException {
    private final Set<String> dependencyChain;

    public CyclicDependencyException(String toolId, Set<String> dependencyChain) {
        super("检测到循环依赖: " + dependencyChain, toolId);
        this.dependencyChain = dependencyChain;
    }

    public Set<String> getDependencyChain() {
        return dependencyChain;
    }
}
