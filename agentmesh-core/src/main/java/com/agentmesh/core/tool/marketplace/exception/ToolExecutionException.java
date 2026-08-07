package com.agentmesh.core.tool.marketplace.exception;

import com.agentmesh.core.tool.marketplace.model.ToolExecutionDescriptor;

/** 工具执行异常 */
public class ToolExecutionException extends RuntimeException {
    private final String toolId;
    private final ToolExecutionDescriptor.ExecutionType executionType;

    public ToolExecutionException(String toolId, ToolExecutionDescriptor.ExecutionType executionType,
                                   String message, Throwable cause) {
        super("工具执行失败 [" + executionType + "]: " + toolId + " - " + message, cause);
        this.toolId = toolId;
        this.executionType = executionType;
    }

    public String getToolId() {
        return toolId;
    }
    public ToolExecutionDescriptor.ExecutionType getExecutionType() {
        return executionType;
    }
}
