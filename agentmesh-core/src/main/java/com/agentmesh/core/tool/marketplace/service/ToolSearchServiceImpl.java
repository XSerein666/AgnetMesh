package com.agentmesh.core.tool.marketplace.service;

import com.agentmesh.core.tool.marketplace.model.ToolMetadata;
import com.agentmesh.core.tool.marketplace.repository.ToolRepository;

import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * 工具搜索服务实现。
 * 聚合 Repository 的基础过滤，提供高级搜索逻辑（分词、排序、分类过滤、缓存）。
 */
@Slf4j
public class ToolSearchServiceImpl implements ToolSearchService {

    private final ToolRepository toolRepository;

    public ToolSearchServiceImpl(ToolRepository toolRepository) {
        this.toolRepository = toolRepository;
    }

    @Override
    public ToolRepository.SearchResult search(SearchRequest request) {
        // 策略：先全量过滤再分页，避免分类过滤在分页之后执行导致结果不准确
        if (request.getCategory() != null && !request.getCategory().isBlank()) {
            // 分类搜索：先获取全量匹配，再按分类过滤，最后分页
            List<ToolMetadata> allPublished = toolRepository.findAllPublished();
            List<ToolMetadata> categoryFiltered = allPublished.stream()
                    .filter(m -> request.getCategory().equals(m.getCategory()))
                    .toList();

            // 关键词筛选（在分类过滤后的集合内搜索）
            List<ToolMetadata> keywordMatched;
            if (request.getQuery() != null && !request.getQuery().isBlank()) {
                keywordMatched = categoryFiltered.stream()
                        .filter(m -> matchesKeyword(m, request.getQuery()))
                        .toList();
            } else {
                keywordMatched = categoryFiltered;
            }

            long total = keywordMatched.size();
            List<ToolMetadata> page = keywordMatched.stream()
                    .skip((long) request.getPage() * request.getSize())
                    .limit(request.getSize())
                    .toList();
            return new ToolRepository.SearchResult(page, total);
        }

        // 无分类过滤：直接通过 Repository 搜索
        return toolRepository.search(
                request.getQuery(), request.getPage() * request.getSize(), request.getSize());
    }

    private boolean matchesKeyword(ToolMetadata m, String keyword) {
        String lower = keyword.toLowerCase();
        return (m.getName() != null && m.getName().toLowerCase().contains(lower))
                || (m.getDescription() != null && m.getDescription().toLowerCase().contains(lower))
                || (m.getTags() != null && m.getTags().stream().anyMatch(t -> t.toLowerCase().contains(lower)));
    }
}
