package com.agentmesh.core.tool.marketplace.service;

import com.agentmesh.core.tool.marketplace.repository.ToolRepository;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工具搜索服务。
 * 聚合 Repository 的基础过滤，提供高级搜索逻辑（分词、排序、分类过滤、缓存）。
 */
public interface ToolSearchService {

    /**
     * 搜索工具（支持关键词 + 分类过滤 + 分页）。
     */
    ToolRepository.SearchResult search(SearchRequest request);

    /**
     * 搜索请求。
     * category 字段支持按分类过滤。
     */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    class SearchRequest {
        /** 搜索关键词 */
        private String query;
        /** 分类过滤（可选，null 表示不限分类） */
        private String category;
        /** 页码（0-based） */
        private int page = 0;
        /** 每页大小 */
        private int size = 20;
    }
}
