package com.agentmesh.core.infrastructure;

/**
 * 统一业务异常
 */
public class AgentMeshException extends RuntimeException {

    private final int code;

    public AgentMeshException(int code, String message) {
        super(message);
        this.code = code;
    }

    public AgentMeshException(String message) {
        super(message);
        this.code = 500;
    }

    public int getCode() {
        return code;
    }
}