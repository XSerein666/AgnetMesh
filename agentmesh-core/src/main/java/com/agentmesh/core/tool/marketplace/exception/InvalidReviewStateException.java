package com.agentmesh.core.tool.marketplace.exception;

import com.agentmesh.core.tool.marketplace.model.ToolMetadata;

/** 非法审核状态转换 */
public class InvalidReviewStateException extends ToolMarketplaceException {
    private final ToolMetadata.ToolStatus currentStatus;

    public InvalidReviewStateException(String message, String toolId, ToolMetadata.ToolStatus currentStatus) {
        super(message, toolId);
        this.currentStatus = currentStatus;
    }

    public ToolMetadata.ToolStatus getCurrentStatus() {
        return currentStatus;
    }
}
