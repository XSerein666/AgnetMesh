package com.agentmesh.core.tool.marketplace.repository;

import com.agentmesh.core.tool.marketplace.model.ToolReview;

import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * 内存评价仓库实现。
 * 使用 ConcurrentHashMap + CopyOnWriteArrayList 保证并发安全。
 */
@Slf4j
public class InMemoryReviewRepository implements ReviewRepository {

    /** key = toolId, value = 该工具的评价列表 */
    private final Map<String, CopyOnWriteArrayList<ToolReview>> store = new ConcurrentHashMap<>();

    @Override
    public ToolReview save(ToolReview review) {
        store.computeIfAbsent(review.getToolId(), k -> new CopyOnWriteArrayList<>())
                .add(review);
        log.debug("[ReviewRepo] 保存评价: toolId={}, reviewer={}, rating={}",
                review.getToolId(), review.getReviewer(), review.getRating());
        return review;
    }

    @Override
    public List<ToolReview> findByToolId(String toolId) {
        List<ToolReview> reviews = store.get(toolId);
        return reviews != null ? List.copyOf(reviews) : List.of();
    }

    @Override
    public List<ToolReview> findByReviewer(String reviewer) {
        return store.values().stream()
                .flatMap(list -> List.copyOf(list).stream())
                .filter(r -> reviewer.equals(r.getReviewer()))
                .collect(Collectors.toList());
    }

    @Override
    public void deleteById(String reviewId) {
        store.values().forEach(list ->
                list.removeIf(r -> reviewId.equals(r.getReviewId())));
    }

    @Override
    public long countByToolId(String toolId) {
        List<ToolReview> reviews = store.get(toolId);
        return reviews != null ? reviews.size() : 0;
    }
}
