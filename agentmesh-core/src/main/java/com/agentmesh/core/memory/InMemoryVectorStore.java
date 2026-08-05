package com.agentmesh.core.memory;

import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 内存向量存储实现。
 * 使用关键词匹配 + Jaccard 相似度计算（无需 embedding API）。
 * 生产环境可替换为 PgVectorStore。
 */
@Slf4j
public class InMemoryVectorStore implements VectorStore {

    private final ConcurrentHashMap<String, MemoryItem> store = new ConcurrentHashMap<>();

    @Override
    public void store(MemoryItem item) {
        if (item.getId() == null) {
            item.setId(UUID.randomUUID().toString());
        }
        store.put(item.getId(), item);
        log.debug("[InMemoryVectorStore] 存储记忆: id={}, type={}", item.getId(), item.getType());
    }

    @Override
    public List<MemoryItem> search(String query, int topK) {
        return search(query, topK, null, null);
    }

    @Override
    public List<MemoryItem> search(String query, int topK, String sessionId, String type) {
        if (query == null || query.isEmpty()) return List.of();

        return store.values().stream()
                .filter(item -> {
                    if (sessionId != null && !sessionId.equals(item.getSessionId())) return false;
                    if (type != null && !type.equals(item.getType())) return false;
                    return true;
                })
                .map(item -> {
                    item.setScore(computeSimilarity(query, item.getContent()));
                    return item;
                })
                .filter(item -> item.getScore() > 0)
                .sorted((a, b) -> Double.compare(b.getScore(), a.getScore()))
                .limit(topK)
                .collect(Collectors.toList());
    }

    @Override
    public void delete(String id) {
        store.remove(id);
        log.debug("[InMemoryVectorStore] 删除记忆: id={}", id);
    }

    @Override
    public void clearBySession(String sessionId) {
        store.entrySet().removeIf(entry ->
                sessionId.equals(entry.getValue().getSessionId()));
        log.info("[InMemoryVectorStore] 清理会话记忆: sessionId={}", sessionId);
    }

    /**
     * 计算 Jaccard 相似度（基于字符级 bigram）。
     * 简单高效，无需外部 embedding API。
     */
    private double computeSimilarity(String query, String content) {
        if (content == null || content.isEmpty()) return 0.0;

        Set<String> queryBigrams = toBigrams(query.toLowerCase());
        Set<String> contentBigrams = toBigrams(content.toLowerCase());

        if (queryBigrams.isEmpty() || contentBigrams.isEmpty()) return 0.0;

        Set<String> intersection = new HashSet<>(queryBigrams);
        intersection.retainAll(contentBigrams);
        Set<String> union = new HashSet<>(queryBigrams);
        union.addAll(contentBigrams);

        return (double) intersection.size() / union.size();
    }

    private Set<String> toBigrams(String text) {
        Set<String> bigrams = new HashSet<>();
        for (int i = 0; i < text.length() - 1; i++) {
            bigrams.add(text.substring(i, i + 2));
        }
        return bigrams;
    }
}