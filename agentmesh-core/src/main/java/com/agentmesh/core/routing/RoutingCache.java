package com.agentmesh.core.routing;

import com.agentmesh.core.agent.AgentConfig;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 路由结果缓存：LRU + TTL 双重过期。
 *
 * 缓存的是精排结果（List<RankedAgent>），key 为 (normalizedInput, sortedAgentIds) 的 SHA-256。
 * 惰性驱逐：get 时逐出过期条目；put 时若超过 maxSize 逐出最旧条目。
 */
@Slf4j
public class RoutingCache {

    private final int maxSize;
    private final Duration ttl;
    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public RoutingCache(int maxSize, Duration ttl) {
        this.maxSize = maxSize;
        this.ttl = ttl;
    }

    /**
     * 构建缓存 key：normalize(input) + sorted(agentIds) → SHA-256 hex。
     */
    public static String buildKey(String input, List<AgentConfig> candidates) {
        String normalized = input.toLowerCase().replaceAll("[^a-z0-9\\u4e00-\\u9fff]", "").trim();
        String agentIds = candidates.stream()
                .map(AgentConfig::getAgentId)
                .sorted()
                .collect(Collectors.joining(","));
        String raw = normalized + "|" + agentIds;
        return sha256(raw);
    }

    public Optional<List<RankedAgent>> get(String key) {
        CacheEntry entry = cache.get(key);
        if (entry == null) {
            return Optional.empty();
        }
        if (entry.isExpired()) {
            cache.remove(key);
            return Optional.empty();
        }
        entry.lastAccessTime = Instant.now();
        return Optional.of(entry.value);
    }

    /**
     * 写入缓存。
     * put + evictOldest 整体加 synchronized，避免并发写时超 maxSize。
     * 读路径仍无锁，依赖 ConcurrentHashMap 保证可见性。
     */
    public synchronized void put(String key, List<RankedAgent> value) {
        if (cache.size() >= maxSize) {
            evictOldest();
        }
        cache.put(key, new CacheEntry(value, ttl));
    }

    public void clear() {
        cache.clear();
    }

    public int size() {
        return cache.size();
    }

    private void evictOldest() {
        String oldest = null;
        Instant oldestTime = Instant.MAX;
        for (Map.Entry<String, CacheEntry> e : cache.entrySet()) {
            if (e.getValue().lastAccessTime.isBefore(oldestTime)) {
                oldestTime = e.getValue().lastAccessTime;
                oldest = e.getKey();
            }
        }
        if (oldest != null) {
            cache.remove(oldest);
        }
    }

    public static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    private static class CacheEntry {
        final List<RankedAgent> value;
        final Instant createdAt;
        final Instant expiresAt;
        volatile Instant lastAccessTime;

        CacheEntry(List<RankedAgent> value, Duration ttl) {
            this.value = value;
            this.createdAt = Instant.now();
            this.expiresAt = createdAt.plus(ttl);
            this.lastAccessTime = createdAt;
        }

        boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }
}
