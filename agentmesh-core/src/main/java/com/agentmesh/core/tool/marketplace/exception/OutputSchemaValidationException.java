package com.agentmesh.core.tool.marketplace.exception;

import java.util.List;

/** 输出 Schema 校验失败 */
public class OutputSchemaValidationException extends ToolExecutionException {
    private final List<String> validationErrors;

    public OutputSchemaValidationException(String toolId, List<String> validationErrors) {
        super(toolId, null, "输出 Schema 校验失败: " + validationErrors, null);
        this.validationErrors = validationErrors;
    }

    public List<String> getValidationErrors() {
        return validationErrors;
    }
}
