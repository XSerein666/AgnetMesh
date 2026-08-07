package com.agentmesh.core.routing;

import com.agentmesh.core.agent.AgentConfig;
import com.agentmesh.core.infrastructure.AgentMeshMetrics;
import com.agentmesh.core.infrastructure.TraceIdContext;
import com.agentmesh.core.llm.LlmClient;
import com.agentmesh.core.protocol.AgentSkill;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 两阶段 LLM 路由策略。
 *
 * 阶段1：关键词/tag 粗筛，从轻量元数据中选出 Top-K 候选。
 *         注册 Agent 数 ≤ SKIP_RECALL_THRESHOLD 时跳过粗筛。
 * 阶段2：LLM 精排，仅将候选的 skills 描述、tags、inputSchema 传入 LLM 结构化输出。
 *         低置信度或超时时回退到关键词路由。
 */
@Slf4j
public class LlmRoutingStrategy implements RoutingStrategy {

    private static final int DEFAULT_TOP_K = 5;
    private static final double DEFAULT_CONFIDENCE_THRESHOLD = 0.6;
    private static final Duration DEFAULT_LLM_TIMEOUT = Duration.ofSeconds(5);
    private static final int DEFAULT_SKIP_RECALL_THRESHOLD = 10;

    private final LlmClient llmClient;
    private final RoutingStrategy fallbackStrategy;
    private final ObjectMapper objectMapper;
    private final AgentMeshMetrics metrics;
    private final RoutingCache cache;
    private final int topK;
    private final int skipRecallThreshold;
    private final double confidenceThreshold;
    private final Duration llmTimeout;

    public LlmRoutingStrategy(LlmClient llmClient) {
        this(llmClient, new KeywordRoutingStrategy(), DEFAULT_TOP_K,
                DEFAULT_SKIP_RECALL_THRESHOLD, DEFAULT_CONFIDENCE_THRESHOLD, DEFAULT_LLM_TIMEOUT,
                null, new RoutingCache(0, Duration.ZERO));
    }

    public LlmRoutingStrategy(LlmClient llmClient, RoutingStrategy fallbackStrategy,
                              int topK, double confidenceThreshold, Duration llmTimeout) {
        this(llmClient, fallbackStrategy, topK, DEFAULT_SKIP_RECALL_THRESHOLD,
                confidenceThreshold, llmTimeout,
                null, new RoutingCache(0, Duration.ZERO));
    }

    public LlmRoutingStrategy(LlmClient llmClient, RoutingStrategy fallbackStrategy,
                              int topK, int skipRecallThreshold,
                              double confidenceThreshold, Duration llmTimeout) {
        this(llmClient, fallbackStrategy, topK, skipRecallThreshold,
                confidenceThreshold, llmTimeout,
                null, new RoutingCache(0, Duration.ZERO));
    }

    public LlmRoutingStrategy(LlmClient llmClient, RoutingStrategy fallbackStrategy,
                              int topK, int skipRecallThreshold,
                              double confidenceThreshold, Duration llmTimeout,
                              AgentMeshMetrics metrics, RoutingCache cache) {
        this.llmClient = llmClient;
        this.fallbackStrategy = fallbackStrategy;
        this.objectMapper = new ObjectMapper();
        this.metrics = metrics;
        this.cache = cache;
        this.topK = topK;
        this.skipRecallThreshold = skipRecallThreshold;
        this.confidenceThreshold = confidenceThreshold;
        this.llmTimeout = llmTimeout;
    }

    @Override
    public List<RankedAgent> route(String input, List<AgentConfig> candidates) {
        String traceId = TraceIdContext.get();
        Instant startTotal = Instant.now();

        if (candidates.isEmpty()) {
            return List.of();
        }
        if (candidates.size() == 1) {
            recordTotalLatency(startTotal);
            return List.of(RankedAgent.builder()
                    .agent(candidates.get(0)).confidence(1.0).build());
        }

        // 阶段1：关键词粗筛 → List<RankedAgent>（带 score）
        Instant startRecall = Instant.now();
        List<RankedAgent> recalled = recall(input, candidates);
        if (metrics != null) {
            metrics.recordRoutingRecall(!recalled.isEmpty());
            metrics.recordRoutingLatency("recall", Duration.between(startRecall, Instant.now()));
        }
        log.info("[LlmRouting] 阶段1粗筛: {} → {} 候选, traceId={}",
                candidates.size(), recalled.size(), traceId);

        // 阶段2：LLM 精排（带缓存）
        try {
            List<AgentConfig> rerankCandidates = recalled.stream()
                    .map(RankedAgent::getAgent)
                    .collect(Collectors.toList());

            // 查缓存
            String cacheKey = RoutingCache.buildKey(input, rerankCandidates);
            Optional<List<RankedAgent>> cached = cache.get(cacheKey);
            if (cached.isPresent()) {
                if (metrics != null) {
                    metrics.recordRoutingCache(true);
                    metrics.recordRoutingRerank("success");
                }
                log.debug("[LlmRouting] 缓存命中, traceId={}", traceId);
                recordTotalLatency(startTotal);
                return cached.get();
            }
            if (metrics != null) {
                metrics.recordRoutingCache(false);
            }

            // 缓存未命中 → LLM 调用
            Instant startRerank = Instant.now();
            List<RankedAgent> ranked = rerank(input, recalled, traceId);
            if (metrics != null) {
                metrics.recordRoutingLatency("rerank", Duration.between(startRerank, Instant.now()));
            }

            if (!ranked.isEmpty() && ranked.get(0).getConfidence() >= confidenceThreshold) {
                // 成功：写缓存 + 打点
                cache.put(cacheKey, ranked);
                if (metrics != null) {
                    metrics.recordRoutingRerank("success");
                }
                log.info("[LlmRouting] 阶段2精排完成: topAgent={}, confidence={}, traceId={}",
                        ranked.get(0).getAgent().getAgentId(),
                        ranked.get(0).getConfidence(), traceId);
                // 日志记录 reason（归因用，仅 debug 级别）
                for (RankedAgent ra : ranked) {
                    if (ra.getReason() != null) {
                        log.debug("[LlmRouting]   {} → confidence={}, reason={}",
                                ra.getAgent().getAgentId(), ra.getConfidence(), ra.getReason());
                    }
                }
                recordTotalLatency(startTotal);
                return ranked;
            }
            // 低置信度回退，不缓存
            if (metrics != null) {
                metrics.recordRoutingRerank("fallback_low_confidence");
            }
            log.warn("[LlmRouting] 榜首置信度 {} < 阈值 {}，回退关键词路由, traceId={}",
                    ranked.isEmpty() ? 0 : ranked.get(0).getConfidence(),
                    confidenceThreshold, traceId);
        } catch (Exception e) {
            if (metrics != null) {
                metrics.recordRoutingRerank(
                        e.getMessage() != null && e.getMessage().contains("超时")
                                ? "fallback_timeout" : "fallback_error");
            }
            log.error("[LlmRouting] LLM 精排异常，回退关键词路由, traceId={}", traceId, e);
        }

        recordTotalLatency(startTotal);
        return fallbackStrategy.route(input, candidates);
    }

    private void recordTotalLatency(Instant start) {
        if (metrics != null) {
            metrics.recordRoutingLatency("total", Duration.between(start, Instant.now()));
        }
    }

    // ========== 阶段1：关键词粗筛 ==========

    /** 返回带原始 score 的 RankedAgent 列表 */
    private List<RankedAgent> recall(String input, List<AgentConfig> candidates) {
        if (candidates.size() <= skipRecallThreshold) {
            // 跳过粗筛，直传全部，score=0, confidence 统一标记
            return candidates.stream()
                    .map(c -> RankedAgent.builder().agent(c).score(0).confidence(0.0).build())
                    .collect(Collectors.toList());
        }

        // 打分 → 排序 → Top-K → 归一化 confidence
        List<RankedAgent> scored = candidates.stream()
                .map(c -> {
                    int s = scoreLightweight(c, input);
                    return RankedAgent.builder().agent(c).score(s).confidence(0.0).build();
                })
                .filter(sa -> sa.getScore() > 0)
                .sorted((a, b) -> Integer.compare(b.getScore(), a.getScore()))
                .limit(topK)
                .collect(Collectors.toList());

        // 归一化 confidence：score / maxScore
        if (!scored.isEmpty()) {
            int maxScore = scored.get(0).getScore();
            for (RankedAgent sa : scored) {
                sa.setConfidence(maxScore > 0 ? (double) sa.getScore() / maxScore : 0.0);
            }
        }
        return scored;
    }

    /** 轻量元数据关键词匹配打分 */
    private int scoreLightweight(AgentConfig config, String input) {
        int score = 0;
        String lowerInput = input.toLowerCase();

        if (config.getAgentId() != null
                && lowerInput.contains(config.getAgentId().toLowerCase())) {
            score += 5;
        }
        if (config.getDescription() != null
                && lowerInput.contains(config.getDescription().toLowerCase())) {
            score += 3;
        }
        if (config.getRoutingTags() != null) {
            for (String tag : config.getRoutingTags()) {
                if (lowerInput.contains(tag.toLowerCase())) {
                    score += 2;
                }
            }
        }
        if (config.getRoutingRule() != null) {
            for (String rule : config.getRoutingRule().split(",")) {
                String keyword = rule.split(":")[0].trim();
                if (lowerInput.contains(keyword.toLowerCase())) {
                    score += 4;
                }
            }
        }
        return score;
    }

    // ========== 阶段2：LLM 精排 ==========

    /** 精排，带超时保护。通过 Reactor timeout 取消上游订阅，HTTP 请求真正被取消。 */
    private List<RankedAgent> rerank(String input, List<RankedAgent> recalled, String traceId) {
        // 从 recalled 提取 AgentConfig 列表
        List<AgentConfig> candidates = recalled.stream()
                .map(RankedAgent::getAgent)
                .collect(Collectors.toList());

        String prompt = buildRerankPrompt(input, candidates);
        List<Map<String, Object>> messages = List.of(
                Map.of("role", "system", "content", getSystemPrompt()),
                Map.of("role", "user", "content", prompt)
        );

        return Mono.fromCallable(() -> llmClient.chat(messages))
                .subscribeOn(Schedulers.boundedElastic())
                .timeout(llmTimeout)
                .blockOptional()
                .map(response -> parseRerankResponse(response, candidates))
                .orElseThrow(() -> {
                    log.warn("[LlmRouting] LLM 精排超时 ({}ms), traceId={}",
                            llmTimeout.toMillis(), traceId);
                    return new RuntimeException("LLM 路由超时");
                });
    }

    private String getSystemPrompt() {
        return """
                你是一个智能路由系统。根据用户输入，从候选 Agent 中选择最合适的 Agent。
                请严格返回 JSON 数组，每个元素包含 agentId、confidence(0-1)、reason。
                按 confidence 降序排列。只返回 JSON，不要其他内容。
                
                示例输出：
                [{"agentId":"weather-agent","confidence":0.95,"reason":"用户询问天气"},
                 {"agentId":"general-agent","confidence":0.3,"reason":"也可处理但非专长"}]
                """;
    }

    /** 构建精排 prompt：包含 skills 描述、inputSchema 等 skill 级细节 */
    private String buildRerankPrompt(String input, List<AgentConfig> candidates) {
        StringBuilder sb = new StringBuilder();
        sb.append("用户输入：").append(input).append("\n\n");
        sb.append("候选 Agent：\n");
        for (int i = 0; i < candidates.size(); i++) {
            AgentConfig c = candidates.get(i);
            sb.append("--- Agent ").append(i + 1).append(" ---\n");
            sb.append("agentId: ").append(c.getAgentId()).append("\n");
            sb.append("description: ").append(c.getDescription()).append("\n");

            // tags（轻量区分特征）
            if (c.getRoutingTags() != null && !c.getRoutingTags().isEmpty()) {
                sb.append("tags: ").append(String.join(", ", c.getRoutingTags())).append("\n");
            }

            // skills 描述（name/description/inputSchema，区分相似 Agent 的关键）
            if (c.getSkills() != null && !c.getSkills().isEmpty()) {
                sb.append("skills:\n");
                for (AgentSkill skill : c.getSkills()) {
                    sb.append("  - name: ").append(skill.getName()).append("\n");
                    if (skill.getDescription() != null) {
                        sb.append("    description: ").append(skill.getDescription()).append("\n");
                    }
                    if (skill.getInputSchema() != null) {
                        sb.append("    inputSchema: ").append(skill.getInputSchema()).append("\n");
                    }
                }
            }

            // systemPrompt 截断到 ~200 字，仅作辅助参考
            if (c.getSystemPrompt() != null) {
                String truncated = c.getSystemPrompt().replace("\n", " ");
                if (truncated.length() > 200) {
                    truncated = truncated.substring(0, 200) + "...";
                }
                sb.append("capability: ").append(truncated).append("\n");
            }

            sb.append("\n");
        }
        return sb.toString();
    }

    // ========== 响应解析 ==========

    @SuppressWarnings("unchecked")
    private List<RankedAgent> parseRerankResponse(String response, List<AgentConfig> candidates) {
        // 1. 剥离 markdown 代码围栏
        String json = response.trim();
        if (json.startsWith("```")) {
            int fenceEnd = json.indexOf('\n');
            if (fenceEnd > 0) {
                json = json.substring(fenceEnd + 1);
            }
            if (json.endsWith("```")) {
                json = json.substring(0, json.length() - 3).trim();
            }
        }

        // 2. 提取 JSON 数组
        int start = json.indexOf('[');
        int end = json.lastIndexOf(']');
        if (start >= 0 && end > start) {
            json = json.substring(start, end + 1);
        }

        Map<String, AgentConfig> agentMap = candidates.stream()
                .collect(Collectors.toMap(AgentConfig::getAgentId, c -> c, (a, b) -> a));

        try {
            List<Map<String, Object>> results = objectMapper.readValue(json,
                    new TypeReference<List<Map<String, Object>>>() {});
            return results.stream()
                    .map(m -> {
                        String agentId = (String) m.get("agentId");
                        double confidence = clamp(
                                ((Number) m.getOrDefault("confidence", 0)).doubleValue(),
                                0.0, 1.0);
                        String reason = (String) m.getOrDefault("reason", "");
                        AgentConfig agent = agentMap.get(agentId);
                        if (agent == null) {
                            log.warn("[LlmRouting] LLM 返回未知 agentId: {}", agentId);
                            return null;
                        }
                        return RankedAgent.builder()
                                .agent(agent).confidence(confidence).reason(reason).build();
                    })
                    .filter(Objects::nonNull)
                    .sorted((a, b) -> Double.compare(b.getConfidence(), a.getConfidence()))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("[LlmRouting] LLM 响应解析失败: {}", response, e);
            throw new RuntimeException("LLM 路由响应解析失败", e);
        }
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
