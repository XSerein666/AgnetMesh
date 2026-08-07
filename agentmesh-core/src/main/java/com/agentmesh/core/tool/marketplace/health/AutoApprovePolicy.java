package com.agentmesh.core.tool.marketplace.health;

import com.agentmesh.core.tool.marketplace.model.ToolMetadata;

import lombok.extern.slf4j.Slf4j;

/**
 * 自动通过审核策略。
 * Phase 2 默认策略：所有工具提交自动通过审核，无需人工介入。
 */
@Slf4j
public class AutoApprovePolicy implements ToolReviewPolicy {

    @Override
    public ReviewResult review(ToolMetadata metadata) {
        // 必要字段校验
        if (metadata.getName() == null || metadata.getName().isBlank()) {
            return ReviewResult.rejected("工具名称不能为空");
        }
        if (metadata.getDescription() == null || metadata.getDescription().isBlank()) {
            return ReviewResult.rejected("工具描述不能为空");
        }
        if (metadata.getCategory() == null) {
            return ReviewResult.rejected("工具分类不能为空");
        }
        if (metadata.getVersion() == null) {
            return ReviewResult.rejected("工具版本不能为空");
        }
        if (metadata.getToolId() == null || metadata.getToolId().isBlank()) {
            return ReviewResult.rejected("工具 ID 不能为空");
        }

        log.info("[AutoApprovePolicy] 工具自动审核通过: {}", metadata.getToolId());
        return ReviewResult.approved();
    }
}
