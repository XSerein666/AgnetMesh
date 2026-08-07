package com.agentmesh.core.task;

/**
 * 乐观锁冲突异常
 */
public class OptimisticLockException extends RuntimeException {
    public OptimisticLockException(String message) {
        super(message);
    }
}
