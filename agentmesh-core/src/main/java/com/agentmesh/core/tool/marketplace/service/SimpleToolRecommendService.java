package com.agentmesh.core.tool.marketplace.service;

import com.agentmesh.core.tool.marketplace.model.ToolMetadata;
import com.agentmesh.core.tool.marketplace.repository.ToolRepository;

import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 简单推荐服务实现：基于分类相似度 + 热度的混合推荐。
 */
@Slf4j
public class SimpleToolRecommendService implements ToolRecommendService {

    private final ToolMarketplace marketplace;
    private final ToolRepository toolRepository;

    public SimpleToolRecommendService(ToolMarketplace marketplace, ToolRepository toolRepository) {
        this.marketplace = marketplace;
        this.toolRepository = toolRepository;
    }

    private final Map<String, Set<String>> installHistory = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> viewHistory = new ConcurrentHashMap<>();

    @Override
    public List<ToolMetadata> recommend(String agentId, int limit) {
        Set<String> installed = installHistory.getOrDefault(agentId, Set.of());

        Map<String, Long> categoryPreference = installed.stream()
                .map(toolRepository::findById)
                .flatMap(Optional::stream)
                .collect(Collectors.groupingBy(ToolMetadata::getCategory, Collectors.counting()));

        return toolRepository.findAllPublished().stream()
                .filter(m -> !installed.contains(m.getToolId()))
                .sorted((a, b) -> {
                    long prefA = categoryPreference.getOrDefault(a.getCategory(), 0L);
                    long prefB = categoryPreference.getOrDefault(b.getCategory(), 0L);
                    int cmp = Long.compare(prefB, prefA);
                    if (cmp != 0) {
                        return cmp;
                    }
                    return Integer.compare(
                            (b.getInstallCount() != null ? b.getInstallCount() : 0),
                            (a.getInstallCount() != null ? a.getInstallCount() : 0));
                })
                .limit(limit)
                .toList();
    }

    @Override
    public List<ToolMetadata> recommendSimilar(String toolId, int limit) {
        ToolMetadata target = toolRepository.findById(toolId).orElse(null);
        if (target == null) {
            return List.of();
        }

        return toolRepository.findAllPublished().stream()
                .filter(m -> !m.getToolId().equals(toolId))
                .filter(m -> target.getCategory() != null && target.getCategory().equals(m.getCategory()))
                .sorted((a, b) -> {
                    int cmp = Double.compare(
                            b.getAverageRating() != null ? b.getAverageRating() : 0.0,
                            a.getAverageRating() != null ? a.getAverageRating() : 0.0);
                    if (cmp != 0) {
                        return cmp;
                    }
                    return Integer.compare(
                            b.getInstallCount() != null ? b.getInstallCount() : 0,
                            a.getInstallCount() != null ? a.getInstallCount() : 0);
                })
                .limit(limit)
                .toList();
    }

    @Override
    public List<ToolMetadata> recommendByRole(String agentRole, int limit) {
        return toolRepository.findAllPublished().stream()
                .sorted((a, b) -> Integer.compare(
                        b.getInstallCount() != null ? b.getInstallCount() : 0,
                        a.getInstallCount() != null ? a.getInstallCount() : 0))
                .limit(limit)
                .toList();
    }

    @Override
    public void recordInstall(String agentId, String toolId) {
        installHistory.computeIfAbsent(agentId, k -> ConcurrentHashMap.newKeySet()).add(toolId);
        log.debug("[RecommendService] 记录安装: agent={}, tool={}", agentId, toolId);
    }

    @Override
    public void recordView(String agentId, String toolId) {
        viewHistory.computeIfAbsent(agentId, k -> ConcurrentHashMap.newKeySet()).add(toolId);
        log.debug("[RecommendService] 记录浏览: agent={}, tool={}", agentId, toolId);
    }
}
