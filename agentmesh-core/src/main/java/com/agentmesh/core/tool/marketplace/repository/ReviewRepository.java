package com.agentmesh.core.tool.marketplace.repository;

import com.agentmesh.core.tool.marketplace.model.ToolReview;

import java.util.List;

/**
 * 评价仓库接口。
 * 管理工具评价的持久化存储。
 */
public interface ReviewRepository {

    ToolReview save(ToolReview review);

    List<ToolReview> findByToolId(String toolId);

    List<ToolReview> findByReviewer(String reviewer);

    void deleteById(String reviewId);

    long countByToolId(String toolId);
}
