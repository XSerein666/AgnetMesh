package com.agentmesh.core.llm;

import java.util.function.Supplier;

/**
 * LLM 调用熔断器接口（预留，后续集成 Resilience4j）
 */
public interface LlmCircuitBreaker {
    boolean isOpen();
    void recordSuccess();
    void recordFailure();
    <T> T execute(Supplier<T> call, Supplier<T> fallback);
}