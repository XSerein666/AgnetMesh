package com.agentmesh.core.tool.marketplace.service;

import com.agentmesh.core.tool.marketplace.model.ToolMetadata;

import java.util.List;

/**
 * 工具推荐服务。
 * 根据 Agent 画像和使用历史，推荐可能感兴趣的工具。
 */
public interface ToolRecommendService {

    /**
     * 为指定 Agent 推荐工具。
     * @param agentId Agent ID
     * @param limit 推荐数量上限
     * @return 推荐工具列表（按推荐度降序）
     */
    List<ToolMetadata> recommend(String agentId, int limit);

    /**
     * 基于相似工具推荐（"看了这个工具的人也看了..."）。
     * @param toolId 参考工具 ID
     * @param limit 推荐数量上限
     */
    List<ToolMetadata> recommendSimilar(String toolId, int limit);

    /**
     * 基于当前 Agent 角色推荐（上下文感知）。
     * @param agentRole Agent 角色（如 "data_analyst", "developer"）
     * @param limit 推荐数量上限
     */
    List<ToolMetadata> recommendByRole(String agentRole, int limit);

    /**
     * 记录工具安装事件，用于更新推荐模型。
     */
    void recordInstall(String agentId, String toolId);

    /**
     * 记录工具浏览事件，用于更新推荐模型。
     */
    void recordView(String agentId, String toolId);
}
