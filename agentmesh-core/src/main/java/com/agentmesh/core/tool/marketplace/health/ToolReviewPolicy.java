package com.agentmesh.core.tool.marketplace.health;

import com.agentmesh.core.tool.marketplace.model.ToolMetadata;

/**
 * 工具审核策略。
 * 可插拔设计，支持自动通过、人工审核、AI 审核等多种策略。
 */
public interface ToolReviewPolicy {

    /**
     * 审核工具提交。
     * @param metadata 工具元数据
     * @return 审核结果
     */
    ReviewResult review(ToolMetadata metadata);

    class ReviewResult {
        private final boolean approved;
        private final String reason;

        public ReviewResult(boolean approved, String reason) {
            this.approved = approved;
            this.reason = reason;
        }

        public static ReviewResult approved() {
            return new ReviewResult(true, "自动通过");
        }

        public static ReviewResult rejected(String reason) {
            return new ReviewResult(false, reason);
        }

        public boolean isApproved() {
            return approved;
        }
        public String getReason() {
            return reason;
        }
    }
}
