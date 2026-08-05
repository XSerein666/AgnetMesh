package com.agentmesh.core.agent;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;

/**
 * Agent 可观测性指标
 * 通过 Micrometer 暴露指标到 Prometheus，配合 Grafana 可视化
 */
@Slf4j
public class AgentMetrics {

    private final MeterRegistry registry;
    private final Timer llmCallTimer;
    private final Timer toolCallTimer;

    public AgentMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.llmCallTimer = Timer.builder("agent.llm.call")
                .description("LLM 调用耗时").register(registry);
        this.toolCallTimer = Timer.builder("agent.tool.call")
                .description("Tool 调用耗时").register(registry);
    }

    /** 记录 LLM 调用 */
    public void recordLlmCall(String model, long durationMs, int tokensUsed) {
        llmCallTimer.record(durationMs, TimeUnit.MILLISECONDS);
        registry.counter("agent.llm.tokens", "model", model).increment(tokensUsed);
        log.info("[AgentMetrics] LLM调用: model={}, duration={}ms, tokens={}", model, durationMs, tokensUsed);
    }

    /** 记录 Tool 调用 */
    public void recordToolCall(String toolName, long durationMs, boolean success) {
        toolCallTimer.record(durationMs, TimeUnit.MILLISECONDS);
        registry.counter("agent.tool.calls", "tool", toolName, "status", success ? "success" : "error")
                .increment();
        log.info("[AgentMetrics] Tool调用: tool={}, duration={}ms, success={}", toolName, durationMs, success);
    }

    /** 记录 Agent 执行轮次 */
    public void recordLoop(int loopCount, boolean finished) {
        registry.counter("agent.loop", "finished", String.valueOf(finished)).increment();
        log.info("[AgentMetrics] Agent推理: loops={}, finished={}", loopCount, finished);
    }
}