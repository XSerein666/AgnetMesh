package com.agentmesh.core.tool.marketplace.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 工具元数据。
 * 描述一个工具的完整信息，供市场展示和搜索使用。
 * 通过 executionDescriptor 连接到可执行代码。
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "工具元数据")
public class ToolMetadata {

    /** 工具唯一 ID（全局唯一，格式：{publisher}:{toolName}）。
     * 使用冒号分隔发布者和工具名，与 REST 路径中的 '/' 避免冲突。 */
    @Schema(description = "工具唯一 ID（格式：{publisher}:{toolName}）", example = "publisher:weather-tool")
    private String toolId;

    /** 工具名称 */
    @Schema(description = "工具名称", example = "天气查询工具")
    private String name;

    /** 工具描述 */
    @Schema(description = "工具描述", example = "查询全球城市的天气信息，支持实时天气和预报")
    private String description;

    /** 工具分类 */
    @Schema(description = "工具分类", example = "NETWORK")
    private String category;

    /** 标签列表 */
    @Schema(description = "标签列表", example = "[\"weather\", \"api\", \"real-time\"]")
    private List<String> tags;

    /** 发布者 Agent ID */
    @Schema(description = "发布者 Agent ID", example = "agent-weather")
    private String publisher;

    /** 当前版本 */
    @Schema(description = "当前版本号")
    private ToolVersion version;

    /** 所有可用版本列表 */
    @Schema(description = "所有可用版本列表")
    private List<ToolVersion> availableVersions;

    /** 工具输入 Schema */
    @Schema(description = "工具输入 Schema（JSON Schema 格式）")
    private Map<String, Object> inputSchema;

    /** 工具输出 Schema */
    @Schema(description = "工具输出 Schema（JSON Schema 格式）")
    private Map<String, Object> outputSchema;

    /** 工具状态 */
    @Schema(description = "工具状态", example = "PUBLISHED")
    private ToolStatus status;

    /** 工具执行描述符（元数据→可执行代码的桥梁） */
    @Schema(description = "工具执行描述符")
    private ToolExecutionDescriptor executionDescriptor;

    /** 平均评分（0-5） */
    @Schema(description = "平均评分（0-5）", example = "4.5", minimum = "0", maximum = "5")
    private Double averageRating;

    /** 评价总数 */
    @Schema(description = "评价总数", example = "128")
    private Integer reviewCount;

    /** 安装次数 */
    @Schema(description = "安装次数", example = "1024")
    private Integer installCount;

    /** 创建时间 */
    @Builder.Default
    @Schema(description = "创建时间", example = "2026-08-06T10:30:00Z")
    private Instant createdAt = Instant.now();

    /** 更新时间 */
    @Builder.Default
    @Schema(description = "更新时间", example = "2026-08-06T12:00:00Z")
    private Instant updatedAt = Instant.now();

    /** 使用示例 */
    @Schema(description = "使用示例", example = "{\"city\": \"Beijing\"}")
    private String usageExample;

    /** 前置依赖（依赖的其他工具 ID 列表） */
    @Schema(description = "前置依赖的工具 ID 列表")
    private List<String> dependencies;

    /**
     * 工具状态枚举。
     *
     * 状态转换规则（状态机）：
     * PENDING_REVIEW → PUBLISHED  （审核通过）
     * PENDING_REVIEW → REJECTED   （审核驳回）
     * REJECTED       → PENDING_REVIEW （修改后重新提交）
     * PUBLISHED      → DEPRECATED （管理员下架）
     * DEPRECATED     → PUBLISHED  （管理员重新上架）
     * 非法转换（会抛异常）：
     *   PUBLISHED → PENDING_REVIEW / REJECTED（已发布不能回退到审核状态）
     *   DEPRECATED → REJECTED / PENDING_REVIEW（下架不能直接变为审核）
     *   REJECTED → PUBLISHED / DEPRECATED（驳回必须先重新提交审核）
     */
    public enum ToolStatus {
        PUBLISHED,
        PENDING_REVIEW,
        DEPRECATED,
        REJECTED;

        /**
         * 检查从当前状态到目标状态的转换是否合法。
         */
        public boolean canTransitionTo(ToolStatus target) {
            return switch (this) {
                case PENDING_REVIEW -> target == PUBLISHED || target == REJECTED;
                case REJECTED       -> target == PENDING_REVIEW;
                case PUBLISHED      -> target == DEPRECATED;
                case DEPRECATED     -> target == PUBLISHED;
            };
        }
    }
}
