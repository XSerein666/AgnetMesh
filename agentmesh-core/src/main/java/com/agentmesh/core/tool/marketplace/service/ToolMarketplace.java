package com.agentmesh.core.tool.marketplace.service;

import com.agentmesh.core.tool.Tool;
import com.agentmesh.core.tool.marketplace.model.ToolMetadata;
import com.agentmesh.core.tool.marketplace.model.ToolReview;
import com.agentmesh.core.tool.marketplace.model.ToolVersion;
import com.agentmesh.core.tool.marketplace.repository.ToolRepository;
import java.util.List;

/**
 * 工具市场核心服务。
 * 提供工具发布、审核、搜索、评价等功能。
 */
public interface ToolMarketplace {

    // ========== 发布与审核 ==========

    /**
     * 提交工具到市场（待审核）。
     * 发布者调用，传入本地 Tool 实例和元数据。
     * 内部自动构建 LOCAL_BEAN 类型执行描述符。
     */
    ToolMetadata submit(Tool<?, ?> tool, ToolMetadata metadata);

    /**
     * 审核工具（管理员操作）。
     * 状态机约束：只能从 PENDING_REVIEW 转换到 PUBLISHED 或 REJECTED。
     */
    ToolMetadata review(String toolId, ToolVersion version, boolean approved, String reason);

    /**
     * 下架工具（管理员操作）。
     * 状态机约束：只能从 PUBLISHED 转换到 DEPRECATED。
     */
    void deprecate(String toolId);

    // ========== 搜索与发现 ==========

    List<ToolMetadata> listPublished();

    List<ToolMetadata> browseByCategory(String category);

    /** 搜索工具（支持分页）。 */
    ToolRepository.SearchResult search(String keyword, int offset, int limit);

    /** 搜索工具（默认分页）。 */
    List<ToolMetadata> search(String keyword);

    List<ToolMetadata> getPopular(int limit);

    List<ToolMetadata> getTopRated(int limit);

    /** 获取工具详情（含评价列表和指定版本）。 */
    ToolMetadata getDetail(String toolId);

    ToolMetadata getDetail(String toolId, ToolVersion version);

    // ========== 评价 ==========

    /**
     * 为工具评分并添加评价。
     * 内部使用读写锁保证 averageRating 和 reviewCount 的原子更新。
     */
    ToolReview addReview(String toolId, ToolReview review);

    List<ToolReview> getReviews(String toolId);
}
