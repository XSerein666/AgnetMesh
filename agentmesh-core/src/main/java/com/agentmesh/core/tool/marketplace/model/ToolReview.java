package com.agentmesh.core.tool.marketplace.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.Instant;

/**
 * 用户对工具的评价。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "工具评价")
public class ToolReview {

    /** 评价 ID */
    @Schema(description = "评价 ID", example = "review-001")
    private String reviewId;

    /** 被评价的工具 ID */
    @Schema(description = "被评价的工具 ID", example = "publisher:weather-tool")
    private String toolId;

    /** 评价者（Agent ID 或用户 ID） */
    @Schema(description = "评价者 ID", example = "agent-weather")
    private String reviewer;

    /** 评分（1-5） */
    @Schema(description = "评分（1-5）", example = "4", minimum = "1", maximum = "5")
    private int rating;

    /** 评价内容 */
    @Schema(description = "评价内容", example = "非常好用的天气工具，准确率高")
    private String comment;

    /** 评价时间 */
    @Builder.Default
    @Schema(description = "评价时间", example = "2026-08-06T10:30:00Z")
    private Instant createdAt = Instant.now();
}
