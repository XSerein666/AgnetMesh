package com.agentmesh.core.tool.marketplace.exception;

/** 工具丢失（严重错误，如升级回滚失败） */
public class ToolLostException extends ToolMarketplaceException {
    public ToolLostException(String toolId, Throwable cause) {
        super("工具已丢失: " + toolId, toolId, cause);
    }
}
