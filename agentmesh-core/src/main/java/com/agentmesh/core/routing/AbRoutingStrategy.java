package com.agentmesh.core.routing;

import com.agentmesh.core.agent.AgentConfig;
import com.agentmesh.core.infrastructure.AgentMeshMetrics;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * A/B 测试路由策略（Shadow 模式）。
 *
 * 生产流量走主策略（keyword），LLM 路由同步串行执行但不影响实际决策。
 * 注意：影子策略是同步执行，p99 会额外吃满 LLM 延迟。
 * 若需真并行，后续版本改为异步化（注意不把 shadow 结果写进主路径）。
 *
 * 记录指标：
 * - top-1 一致率（match / mismatch / both_empty）
 * - top-K Jaccard 重叠率（K=3，小集群时 K = min(3, candidates.size())）
 * - 两种策略的延迟和置信度
 */
@Slf4j
public class AbRoutingStrategy implements RoutingStrategy {

    private static final int TOP_K_OVERLAP = 3;

    private final RoutingStrategy primary;
    private final RoutingStrategy shadow;
    private final AgentMeshMetrics metrics;
    private final double sampleRate;

    public AbRoutingStrategy(RoutingStrategy primary, RoutingStrategy shadow,
                              AgentMeshMetrics metrics, double sampleRate) {
        this.primary = primary;
        this.shadow = shadow;
        this.metrics = metrics;
        this.sampleRate = sampleRate;
    }

    @Override
    public List<RankedAgent> route(String input, List<AgentConfig> candidates) {
        // 主策略：生产路径
        Instant startPrimary = Instant.now();
        List<RankedAgent> primaryResult = primary.route(input, candidates);
        long primaryMs = Duration.between(startPrimary, Instant.now()).toMillis();

        // 影子策略：采样执行（同步串行，所有指标仅在采样内记录，保持口径对称）
        if (ThreadLocalRandom.current().nextDouble() < sampleRate) {
            Instant startShadow = Instant.now();
            try {
                List<RankedAgent> shadowResult = shadow.route(input, candidates);
                long shadowMs = Duration.between(startShadow, Instant.now()).toMillis();

                // 记录延迟
                metrics.recordAbLatency("keyword", primaryMs);
                metrics.recordAbLatency("llm", shadowMs);

                // top-1 一致率（含 both_empty 独立 outcome）
                String consistency = classifyConsistency(primaryResult, shadowResult);
                metrics.recordAbConsistency(consistency);

                // top-K Jaccard 重叠率（K 自适应小集群）
                int k = Math.min(TOP_K_OVERLAP, candidates.size());
                double jaccard = computeJaccard(primaryResult, shadowResult, k);
                metrics.recordAbOverlap(k, jaccard);

                // 两种策略的 top-1 置信度
                double primaryConf = primaryResult.isEmpty() ? 0.0
                        : primaryResult.get(0).getConfidence();
                double shadowConf = shadowResult.isEmpty() ? 0.0
                        : shadowResult.get(0).getConfidence();
                metrics.recordAbConfidence("keyword", primaryConf);
                metrics.recordAbConfidence("llm", shadowConf);

                log.debug("[AB] input={}, consistency={}, jaccard@{}={}, keywordMs={}, llmMs={}, "
                        + "keywordConf={}, llmConf={}",
                        input, consistency, k, jaccard, primaryMs, shadowMs,
                        primaryConf, shadowConf);
            } catch (Exception e) {
                log.warn("[AB] LLM 影子路由异常: {}", e.getMessage());
            }
        }

        return primaryResult;
    }

    /**
     * 分类一致率：match / mismatch / both_empty。
     * 双方都空不计入 match（两个策略都路由失败时恰恰最该报警）。
     */
    private String classifyConsistency(List<RankedAgent> a, List<RankedAgent> b) {
        if (a.isEmpty() && b.isEmpty()) {
            return "both_empty";
        }
        if (a.isEmpty() || b.isEmpty()) {
            return "mismatch";
        }
        return a.get(0).getAgent().getAgentId()
                .equals(b.get(0).getAgent().getAgentId()) ? "match" : "mismatch";
    }

    /**
     * 计算 top-K Jaccard 重叠率。
     * Jaccard = |A ∩ B| / |A ∪ B|，取前 K 个 agentId。
     */
    private double computeJaccard(List<RankedAgent> a, List<RankedAgent> b, int k) {
        Set<String> setA = a.stream()
                .limit(k)
                .map(r -> r.getAgent().getAgentId())
                .collect(Collectors.toSet());
        Set<String> setB = b.stream()
                .limit(k)
                .map(r -> r.getAgent().getAgentId())
                .collect(Collectors.toSet());

        Set<String> intersection = new HashSet<>(setA);
        intersection.retainAll(setB);

        Set<String> union = new HashSet<>(setA);
        union.addAll(setB);

        if (union.isEmpty()) {
            return 1.0;
        }
        return (double) intersection.size() / union.size();
    }
}
