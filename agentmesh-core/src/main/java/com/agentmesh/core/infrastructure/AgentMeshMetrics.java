package com.agentmesh.core.infrastructure;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.time.Duration;

/**
 * AgentMesh 核心指标。
 *
 * 设计约束：
 * - 所有 Timer 配置 publishPercentiles(0.5, 0.95, 0.99) + publishPercentileHistogram()
 * - 远程调用 tag 使用 agent_id 而非 remote_url（防止 localhost vs 容器名分裂序列）
 * - status 枚举：SUCCESS / FAILED / TIMEOUT
 * - traceId 不进指标 tag（防止基数爆炸）
 */
public class AgentMeshMetrics {

    private final MeterRegistry registry;

    public AgentMeshMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    // ========== Agent 指标 ==========

    public void recordAgentInvocation(String agentId, String status) {
        Counter.builder("agentmesh.agent.invocations")
                .tag("agent_id", agentId)
                .tag("status", status)
                .register(registry)
                .increment();
    }

    public Timer.Sample startAgentTimer() {
        return Timer.start(registry);
    }

    public void stopAgentTimer(Timer.Sample sample, String agentId) {
        sample.stop(Timer.builder("agentmesh.agent.latency")
                .tag("agent_id", agentId)
                .publishPercentiles(0.5, 0.95, 0.99)
                .publishPercentileHistogram()
                .minimumExpectedValue(Duration.ofMillis(1))
                .maximumExpectedValue(Duration.ofMinutes(1))
                .register(registry));
    }

    // ========== Tool 指标 ==========

    public void recordToolExecution(String toolId, String status) {
        Counter.builder("agentmesh.tool.executions")
                .tag("tool_id", toolId)
                .tag("status", status)
                .register(registry)
                .increment();
    }

    public Timer.Sample startToolTimer() {
        return Timer.start(registry);
    }

    public void stopToolTimer(Timer.Sample sample, String toolId) {
        sample.stop(Timer.builder("agentmesh.tool.latency")
                .tag("tool_id", toolId)
                .publishPercentiles(0.5, 0.95, 0.99)
                .publishPercentileHistogram()
                .minimumExpectedValue(Duration.ofMillis(1))
                .maximumExpectedValue(Duration.ofSeconds(30))
                .register(registry));
    }

    // ========== Remote 指标（tag 用 agent_id 而非 url） ==========

    public void recordRemoteCall(String agentId, String status) {
        Counter.builder("agentmesh.remote.calls")
                .tag("agent_id", agentId)
                .tag("status", status)
                .register(registry)
                .increment();
    }

    public Timer.Sample startRemoteTimer() {
        return Timer.start(registry);
    }

    public void stopRemoteTimer(Timer.Sample sample, String agentId) {
        sample.stop(Timer.builder("agentmesh.remote.latency")
                .tag("agent_id", agentId)
                .publishPercentiles(0.5, 0.95, 0.99)
                .publishPercentileHistogram()
                .minimumExpectedValue(Duration.ofMillis(1))
                .maximumExpectedValue(Duration.ofSeconds(60))
                .register(registry));
    }

    // ========== LLM 指标 ==========

    public void recordLlmCall(String provider) {
        Counter.builder("agentmesh.llm.calls")
                .tag("provider", provider)
                .register(registry)
                .increment();
    }

    public void recordLlmTokens(String provider, String type, long count) {
        Counter.builder("agentmesh.llm.tokens")
                .tag("provider", provider)
                .tag("type", type)
                .register(registry)
                .increment(count);
    }

    // ========== Routing 指标 ==========

    /** 粗筛命中率。hit = 至少 1 个候选得分 > 0 */
    public void recordRoutingRecall(boolean hit) {
        Counter.builder("agentmesh.routing.recall")
                .tag("outcome", hit ? "hit" : "miss")
                .register(registry)
                .increment();
    }

    /** 精排结果分类：success / fallback_low_confidence / fallback_timeout / fallback_error */
    public void recordRoutingRerank(String outcome) {
        Counter.builder("agentmesh.routing.rerank")
                .tag("outcome", outcome)
                .register(registry)
                .increment();
    }

    /** 路由各阶段耗时 */
    public void recordRoutingLatency(String phase, Duration duration) {
        Timer.builder("agentmesh.routing.latency")
                .tag("phase", phase)
                .publishPercentiles(0.5, 0.95, 0.99)
                .publishPercentileHistogram()
                .minimumExpectedValue(Duration.ofMillis(1))
                .maximumExpectedValue(Duration.ofSeconds(10))
                .register(registry)
                .record(duration);
    }

    /** 缓存命中率 */
    public void recordRoutingCache(boolean hit) {
        Counter.builder("agentmesh.routing.cache")
                .tag("outcome", hit ? "hit" : "miss")
                .register(registry)
                .increment();
    }

    // ========== Orchestration 指标 ==========

    /**
     * 编排调用计数。
     * @param orchestratorType sequential | parallel | conditional
     * @param status           SUCCESS | FAILED
     */
    public void recordOrchestration(String orchestratorType, String status) {
        Counter.builder("agentmesh.orchestration.invocations")
                .tag("orchestrator", orchestratorType)
                .tag("status", status)
                .register(registry)
                .increment();
    }

    public Timer.Sample startOrchestrationTimer() {
        return Timer.start(registry);
    }

    public void stopOrchestrationTimer(Timer.Sample sample, String orchestratorType) {
        sample.stop(Timer.builder("agentmesh.orchestration.latency")
                .tag("orchestrator", orchestratorType)
                .publishPercentiles(0.5, 0.95, 0.99)
                .publishPercentileHistogram()
                .minimumExpectedValue(Duration.ofMillis(1))
                .maximumExpectedValue(Duration.ofSeconds(60))
                .register(registry));
    }

    /**
     * Failover 触发计数（仅 ConditionalOrchestrator 在 onErrorResume 时调用）。
     * @param failedAgent 触发 failover 的源 Agent ID
     * @param nextAgent   切换到的目标 Agent ID
     */
    public void recordFailover(String failedAgent, String nextAgent) {
        Counter.builder("agentmesh.orchestration.failover")
                .tag("failed_agent", failedAgent)
                .tag("next_agent", nextAgent)
                .register(registry)
                .increment();
    }

    // ========== A/B 测试指标 ==========

    /**
     * A/B 路由 top-1 一致率。
     * outcome: match | mismatch | both_empty
     * 双方都空不计入 match（双空时一致率虚高，恰恰最该报警）。
     */
    public void recordAbConsistency(String outcome) {
        Counter.builder("agentmesh.routing.ab.consistency")
                .tag("outcome", outcome)
                .register(registry)
                .increment();
    }

    /**
     * A/B top-K Jaccard 重叠率。
     * @param k 实际使用的 K 值（小集群时自适应）
     * @param jaccard Jaccard 系数 [0.0, 1.0]
     */
    public void recordAbOverlap(int k, double jaccard) {
        io.micrometer.core.instrument.DistributionSummary.builder("agentmesh.routing.ab.overlap")
                .tag("k", String.valueOf(k))
                .publishPercentiles(0.5, 0.95, 0.99)
                .publishPercentileHistogram()
                .minimumExpectedValue(0.0)
                .maximumExpectedValue(1.0)
                .register(registry)
                .record(jaccard);
    }

    /** A/B 两种策略延迟 */
    public void recordAbLatency(String strategy, long millis) {
        Timer.builder("agentmesh.routing.ab.latency")
                .tag("strategy", strategy)
                .publishPercentiles(0.5, 0.95, 0.99)
                .publishPercentileHistogram()
                .minimumExpectedValue(Duration.ofMillis(1))
                .maximumExpectedValue(Duration.ofSeconds(10))
                .register(registry)
                .record(Duration.ofMillis(millis));
    }

    /** 路由置信度分布（同时上报 primary 和 shadow 的 top-1 置信度） */
    public void recordAbConfidence(String strategy, double confidence) {
        io.micrometer.core.instrument.DistributionSummary.builder("agentmesh.routing.ab.confidence")
                .tag("strategy", strategy)
                .publishPercentiles(0.5, 0.95, 0.99)
                .publishPercentileHistogram()
                .minimumExpectedValue(0.0)
                .maximumExpectedValue(1.0)
                .register(registry)
                .record(confidence);
    }
}
