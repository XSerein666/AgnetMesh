package com.jewel.a2a.common.exception;

/**
 * 统一业务异常
 */
public class A2AException extends RuntimeException {

    private final int code;

    public A2AException(int code, String message) {
        super(message);
        this.code = code;
    }

    public A2AException(String message) {
        super(message);
        this.code = 500;
    }

    public int getCode() {
        return code;
    }
}
