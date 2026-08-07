package com.agentmesh.core.tool.marketplace.repository;

import com.agentmesh.core.tool.marketplace.model.ToolMetadata;
import com.agentmesh.core.tool.marketplace.model.ToolVersion;

import java.util.List;
import java.util.Optional;

/**
 * 工具仓库接口。
 * 管理工具元数据的持久化存储。
 */
public interface ToolRepository {

    ToolMetadata save(ToolMetadata metadata);

    Optional<ToolMetadata> findById(String toolId);

    Optional<ToolMetadata> findByIdAndVersion(String toolId, ToolVersion version);

    List<ToolMetadata> findAllVersions(String toolId);

    List<ToolMetadata> findAllPublished();

    List<ToolMetadata> findByCategory(String category);

    List<ToolMetadata> findByTag(String tag);

    List<ToolMetadata> findByPublisher(String publisher);

    /**
     * 搜索工具（按名称、描述、标签模糊匹配）。
     * @param keyword 搜索关键词
     * @param offset 分页偏移
     * @param limit 分页大小
     * @return 搜索结果
     */
    SearchResult search(String keyword, int offset, int limit);

    void deleteById(String toolId);

    void deleteByIdAndVersion(String toolId, ToolVersion version);

    long count();

    /**
     * 持久化当前数据到文件（用于 JSON 文件持久化）。
     */
    void flush();

    /**
     * 从文件加载数据（启动时调用）。
     */
    void load();

    /**
     * 搜索结果封装。
     */
    record SearchResult(List<ToolMetadata> items, long total) {}
}
