package com.agentmesh.core.tool.marketplace.exception;

/** 非法状态机转换 */
public class IllegalStateTransitionException extends RuntimeException {
    private final String fromState;
    private final String toState;

    public IllegalStateTransitionException(String fromState, String toState) {
        super("非法状态转换: " + fromState + " → " + toState);
        this.fromState = fromState;
        this.toState = toState;
    }

    public String getFromState() {
        return fromState;
    }
    public String getToState() {
        return toState;
    }
}
