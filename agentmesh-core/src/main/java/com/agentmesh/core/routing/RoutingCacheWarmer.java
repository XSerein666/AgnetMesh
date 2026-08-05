package com.agentmesh.core.routing;

import com.agentmesh.core.agent.AgentConfig;
import com.agentmesh.core.registry.AgentRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 缓存预热器：应用启动后，对预配置的高频输入预计算路由结果。
 *
 * 就绪门控（两阶段）：
 * ① 轮询 AgentRegistry.discover() 直到非空（最长 30s，每 2s 检查一次）
 * ② 等待所有 in-flight descGen 完成（最长 15s，descGen 超时 10s + 缓冲）
 * 超时则 WARN + skip 计数，不写入空候选或即将漂移的缓存条目。
 *
 * 候选构造：必须走 agentRegistry.buildAgentConfig()（唯一事实源），
 * 与运行时路由使用完全相同的 AgentConfig 构建路径，确保缓存 key 一致。
 *
 * 执行方式：@Async 独立线程，不阻塞 ApplicationReadyEvent 监听线程。
 */
@Slf4j
public class RoutingCacheWarmer {

    private static final long POLL_INTERVAL_MS = 2000;
    private static final long MAX_WAIT_MS = 30_000;
    private static final Duration DESCGEN_WAIT = Duration.ofSeconds(15);

    private final RoutingStrategy llmStrategy;
    private final AgentRegistry agentRegistry;
    private final List<String> warmupInputs;

    public RoutingCacheWarmer(RoutingStrategy llmStrategy,
                               AgentRegistry agentRegistry,
                               List<String> warmupInputs) {
        this.llmStrategy = llmStrategy;
        this.agentRegistry = agentRegistry;
        this.warmupInputs = warmupInputs;
    }

    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void warmup() {
        if (warmupInputs.isEmpty()) {
            return;
        }

        // 阶段 1：等待 registry 就绪
        List<String> agentIds = waitForRegistryReady();
        if (agentIds.isEmpty()) {
            log.warn("[CacheWarmer] registry 就绪超时（{}ms），跳过预热，共 {} 条输入未预热",
                    MAX_WAIT_MS, warmupInputs.size());
            return;
        }

        // 阶段 2：等待 descGen 完成（防止 warm 后描述落地导致 key 漂移）
        if (!agentRegistry.awaitDescGenCompletion(DESCGEN_WAIT)) {
            log.warn("[CacheWarmer] descGen 未在 {}s 内完成，继续预热（可能部分 key 漂移，"
                    + "TTL 内自然过期）", DESCGEN_WAIT.toSeconds());
        }

        // 构建候选列表（唯一事实源：agentRegistry.buildAgentConfig()）
        List<AgentConfig> candidates = agentIds.stream()
                .map(agentRegistry::buildAgentConfig)
                .collect(Collectors.toList());

        log.info("[CacheWarmer] 开始预热 {} 条输入（{} 个候选 Agent）...",
                warmupInputs.size(), candidates.size());
        int warmed = 0;
        for (String input : warmupInputs) {
            try {
                llmStrategy.route(input, candidates);
                warmed++;
                log.debug("[CacheWarmer] 预热完成: {}", input);
            } catch (Exception e) {
                log.warn("[CacheWarmer] 预热失败: input={}, error={}", input, e.getMessage());
            }
        }
        log.info("[CacheWarmer] 预热完成: {}/{}", warmed, warmupInputs.size());
    }

    /**
     * 轮询等待 registry 就绪（非空），返回 agentId 列表。
     */
    private List<String> waitForRegistryReady() {
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < MAX_WAIT_MS) {
            List<String> ids = agentRegistry.discover().stream()
                    .map(n -> n.getCard().getAgentId())
                    .collect(Collectors.toList());
            if (!ids.isEmpty()) {
                log.info("[CacheWarmer] registry 就绪，{} 个对端 Agent", ids.size());
                return ids;
            }
            try {
                Thread.sleep(POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return List.of();
            }
        }
        return List.of();
    }
}