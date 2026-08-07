package com.agentmesh.core.registry;

import com.agentmesh.core.protocol.AgentCard;
import com.agentmesh.core.protocol.AgentSkill;
import com.agentmesh.core.routing.AgentDescriptionGenerator;
import com.agentmesh.core.routing.RoutingCache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

/**
 * 内存实现：点对点发现模式。
 * 启动时通过配置文件中的 peers 列表，调用各对端的 /.well-known/agent.json 获取 AgentCard。
 * 对端未启动时不影响本端启动，resolve() 未命中时惰性重拉。
 */
@Slf4j
public class InMemoryAgentRegistry implements AgentRegistry {

    private final Map<String, AgentNode> peers = new ConcurrentHashMap<>();
    private final AgentNode self;
    private final RestTemplate restTemplate;
    private final List<String> peerUrls;
    private final List<Runnable> refreshListeners = new ArrayList<>();

    // === Phase 13：Agent 描述自动生成 ===
    private final AgentDescriptionGenerator descriptionGenerator;
    private final ExecutorService descGenExecutor;
    /** 跟踪 in-flight 的 descGen 任务，防止同 Agent 并发重复生成 */
    private final ConcurrentHashMap<String, CompletableFuture<Void>> pendingDescGen = new ConcurrentHashMap<>();

    public InMemoryAgentRegistry(AgentCard selfCard, RestTemplate restTemplate,
                                  List<String> peerUrls) {
        this(selfCard, restTemplate, peerUrls, null, null);
    }

    public InMemoryAgentRegistry(AgentCard selfCard, RestTemplate restTemplate,
                                  List<String> peerUrls,
                                  AgentDescriptionGenerator descriptionGenerator,
                                  ExecutorService descGenExecutor) {
        this.restTemplate = restTemplate;
        this.peerUrls = new ArrayList<>(peerUrls);
        this.descriptionGenerator = descriptionGenerator;
        this.descGenExecutor = descGenExecutor;

        this.self = AgentNode.builder()
                .card(selfCard)
                .registeredAt(LocalDateTime.now())
                .lastRefreshedAt(LocalDateTime.now())
                .reachable(true)
                .build();

        log.info("[AgentRegistry] 自身 Agent: {} ({})", selfCard.getName(), selfCard.getUrl());

        // 发现对端 Agent（失败不阻塞启动）
        for (String peerUrl : peerUrls) {
            tryPullPeer(peerUrl);
        }
    }

    // ========== 核心方法 ==========

    @Override
    public List<AgentNode> discover() {
        return List.copyOf(peers.values());
    }

    @Override
    public List<AgentNode> discoverBySkill(String skillId) {
        return peers.values().stream()
                .filter(node -> node.getCard().getSkills() != null
                        && node.getCard().getSkills().stream()
                                .anyMatch(s -> skillId.equals(s.getId())))
                .collect(Collectors.toList());
    }

    @Override
    public AgentNode resolve(String agentId) {
        // 自身
        if (agentId.equals(self.getCard().getAgentId())) {
            return self;
        }
        // 缓存命中
        AgentNode cached = peers.get(agentId);
        if (cached != null) {
            return cached;
        }
        // 未命中 → 惰性重拉所有 peer
        log.info("[AgentRegistry] resolve 未命中 agentId={}, 尝试惰性重拉", agentId);
        for (String peerUrl : peerUrls) {
            AgentCard card = tryPullPeer(peerUrl);
            if (card != null && agentId.equals(card.getAgentId())) {
                return peers.get(agentId);
            }
        }
        return null;
    }

    @Override
    public AgentNode self() {
        return self;
    }

    /**
     * 注册 refresh 监听器。
     * 注册时立即触发一次 listener（用于启动时导入远程工具），
     * 后续每次 refresh() 完成时触发。
     */
    public void addRefreshListener(Runnable listener) {
        synchronized (refreshListeners) {
            refreshListeners.add(listener);
        }
        // 立即触发一次（启动导入）
        listener.run();
    }

    /**
     * 刷新所有对端（可由定时任务或外部主动调用）
     */
    public void refresh() {
        log.info("[AgentRegistry] 开始刷新所有对端");
        for (String peerUrl : peerUrls) {
            AgentCard card = tryPullPeer(peerUrl);
            // Phase 13：刷新路径也触发生成（对端 skills 更新后重新生成描述）
            if (card != null && descriptionGenerator != null) {
                AgentNode node = peers.get(card.getAgentId());
                if (node != null) {
                    triggerDescGenIfNeeded(node);
                }
            }
        }
        // 通知所有监听器
        synchronized (refreshListeners) {
            for (Runnable listener : refreshListeners) {
                try {
                    listener.run();
                } catch (Exception e) {
                    log.error("[AgentRegistry] refreshListener 执行异常", e);
                }
            }
        }
    }

    /**
     * 等待所有 in-flight descGen 任务完成（缓存预热就绪门控用）。
     * @param timeout 最长等待时间
     * @return true = 全部完成，false = 超时
     */
    @Override
    public boolean awaitDescGenCompletion(Duration timeout) {
        if (pendingDescGen.isEmpty()) {
            return true;
        }
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        for (CompletableFuture<Void> f : pendingDescGen.values()) {
            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0) {
                return false;
            }
            try {
                f.get(remaining, java.util.concurrent.TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                return false;
            }
        }
        return true;
    }

    // ========== 内部方法 ==========

    private AgentCard tryPullPeer(String peerUrl) {
        try {
            String agentJsonUrl = buildAgentJsonUrl(peerUrl);
            AgentCard card = restTemplate.getForObject(agentJsonUrl, AgentCard.class);
            if (card == null) {
                return null;
            }
            // 补全 url（对端可能没填）
            if (card.getUrl() == null || card.getUrl().isEmpty()) {
                card.setUrl(peerUrl);
            }
            // 补全 skills 的 agentId
            if (card.getSkills() != null) {
                for (AgentSkill skill : card.getSkills()) {
                    if (skill.getAgentId() == null) {
                        skill.setAgentId(card.getAgentId());
                    }
                }
            }
            AgentNode node = AgentNode.builder()
                    .card(card)
                    .registeredAt(LocalDateTime.now())
                    .lastRefreshedAt(LocalDateTime.now())
                    .reachable(true)
                    .build();
            peers.put(card.getAgentId(), node);
            log.info("[AgentRegistry] 发现对端 Agent: {} ({}), 技能数: {}",
                    card.getName(), card.getUrl(),
                    card.getSkills() != null ? card.getSkills().size() : 0);

            // Phase 13：异步生成路由描述（使用专用线程池，非 commonPool）
            if (descriptionGenerator != null) {
                triggerDescGenIfNeeded(node);
            }

            return card;
        } catch (Exception e) {
            log.warn("[AgentRegistry] 无法连接对端 Agent: {}, 原因: {}", peerUrl, e.getMessage());
            markUnreachable(peerUrl);
            return null;
        }
    }

    /**
     * 构建 agent.json URL，处理各种路径情况
     */
    private String buildAgentJsonUrl(String peerUrl) {
        String base = peerUrl.endsWith("/") ? peerUrl.substring(0, peerUrl.length() - 1) : peerUrl;
        return base + "/.well-known/agent.json";
    }

    private void markUnreachable(String peerUrl) {
        for (AgentNode node : peers.values()) {
            if (peerUrl.equals(node.getCard().getUrl())) {
                node.setReachable(false);
                node.setLastRefreshedAt(LocalDateTime.now());
                break;
            }
        }
    }

    // ========== Phase 13：descGen 方法 ==========

    /**
     * 触发描述生成（带去重）。
     * 若同 Agent 已有 in-flight 任务，跳过不重复触发。
     */
    private void triggerDescGenIfNeeded(AgentNode node) {
        String agentId = node.getCard().getAgentId();
        // 指纹未变且已有生成结果 → 跳过
        String currentFingerprint = computeSkillsFingerprint(node.getCard());
        if (node.getGeneratedDescription() != null
                && currentFingerprint.equals(node.getSkillsFingerprint())) {
            log.debug("[DescGen] Agent {} skills 指纹未变，跳过生成", agentId);
            return;
        }
        // 去重：已有 in-flight 任务 → 跳过
        CompletableFuture<Void> existing = pendingDescGen.get(agentId);
        if (existing != null && !existing.isDone()) {
            log.debug("[DescGen] Agent {} 已有 in-flight 生成任务，跳过", agentId);
            return;
        }
        // 提交新任务
        CompletableFuture<Void> future = CompletableFuture.runAsync(
                () -> generateDescriptionIfNeeded(node), descGenExecutor);
        pendingDescGen.put(agentId, future);
        // 完成后清理
        future.whenComplete((v, e) -> pendingDescGen.remove(agentId, future));
    }

    private void generateDescriptionIfNeeded(AgentNode node) {
        AgentCard card = node.getCard();
        var result = descriptionGenerator.generate(card);
        if (result != null) {
            node.setGeneratedDescription(result.description());
            node.setGeneratedRoutingTags(result.routingTags());
            node.setSkillsFingerprint(computeSkillsFingerprint(card));
            log.info("[DescGen] Agent {} 描述已生成: description={}, tags={}",
                    card.getAgentId(), result.description(), result.routingTags());
        }
    }

    /**
     * 计算 skills 指纹。
     * 对 skills 的 name + description 做 SHA-256，用于判断是否需要重新生成描述。
     */
    private String computeSkillsFingerprint(AgentCard card) {
        if (card.getSkills() == null || card.getSkills().isEmpty()) {
            return "";
        }
        String raw = card.getSkills().stream()
                .map(s -> s.getName() + "|" + (s.getDescription() != null ? s.getDescription() : ""))
                .sorted()
                .collect(Collectors.joining("\n"));
        return RoutingCache.sha256(raw);
    }
}
