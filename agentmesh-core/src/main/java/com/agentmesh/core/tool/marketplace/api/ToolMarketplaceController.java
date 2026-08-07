package com.agentmesh.core.tool.marketplace.api;

import com.agentmesh.core.tool.marketplace.health.CategoryRegistry;
import com.agentmesh.core.tool.marketplace.health.ToolHealthChecker;
import com.agentmesh.core.tool.marketplace.install.ToolInstallManager;
import com.agentmesh.core.tool.marketplace.model.ToolMetadata;
import com.agentmesh.core.tool.marketplace.model.ToolReview;
import com.agentmesh.core.tool.marketplace.model.ToolVersion;
import com.agentmesh.core.tool.marketplace.repository.ToolRepository;
import com.agentmesh.core.tool.marketplace.service.ToolMarketplace;
import com.agentmesh.core.tool.marketplace.service.ToolRecommendService;
import com.agentmesh.core.tool.marketplace.service.ToolSearchService;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 工具市场门户 REST API 控制器。
 * 提供工具浏览、搜索、安装、评价、审核等接口。
 */
@RestController
@RequestMapping("/api/v1/tool-marketplace")
@RequiredArgsConstructor
@Tag(name = "工具市场", description = "工具浏览、搜索、安装、评价、审核等接口")
public class ToolMarketplaceController {

    private final ToolMarketplace marketplace;
    private final ToolSearchService searchService;
    private final ToolInstallManager installManager;
    private final ToolHealthChecker healthChecker;
    @SuppressFBWarnings("URF_UNREAD_FIELD")
    // TODO: recommendService 暂未使用，待推荐功能上线后启用
    private final ToolRecommendService recommendService;
    private final CategoryRegistry categoryRegistry;

    // ========== 公开接口 ==========

    @Operation(summary = "获取已发布工具列表", description = "分页获取所有已发布（PUBLISHED 状态）的工具", tags = {"公开接口"})
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "成功返回工具列表"),
            @ApiResponse(responseCode = "401", description = "未认证", content = @Content)
    })
    @GetMapping("/tools")
    public ResponseEntity<List<ToolMetadata>> listPublished(
            @Parameter(description = "页码（0-based）", example = "0") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页大小", example = "20") @RequestParam(defaultValue = "20") int size) {
        List<ToolMetadata> all = marketplace.listPublished();
        int offset = page * size;
        if (offset >= all.size()) {
            return ResponseEntity.ok(List.of());
        }
        int end = Math.min(offset + size, all.size());
        return ResponseEntity.ok(all.subList(offset, end));
    }

    @Operation(summary = "搜索工具", description = "按关键词和分类搜索工具，支持分页", tags = {"公开接口"})
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "成功返回搜索结果",
                    content = @Content(schema = @Schema(implementation = ToolRepository.SearchResult.class))),
            @ApiResponse(responseCode = "401", description = "未认证", content = @Content)
    })
    @GetMapping("/tools/search")
    public ResponseEntity<ToolRepository.SearchResult> search(
            @Parameter(description = "搜索关键词", required = true, example = "weather") @RequestParam String q,
            @Parameter(description = "页码（0-based）", example = "0") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页大小", example = "20") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "分类过滤（可选）", example = "NETWORK") @RequestParam(required = false) String category) {
        return ResponseEntity.ok(searchService.search(
                new ToolSearchService.SearchRequest(q, category, page, size)));
    }

    @Operation(summary = "按分类浏览工具", description = "获取指定分类下的所有已发布工具", tags = {"公开接口"})
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "成功返回分类下的工具列表"),
            @ApiResponse(responseCode = "401", description = "未认证", content = @Content)
    })
    @GetMapping("/tools/category/{category}")
    public ResponseEntity<List<ToolMetadata>> browseByCategory(
            @Parameter(description = "分类标识", required = true, example = "NETWORK") @PathVariable String category) {
        return ResponseEntity.ok(marketplace.browseByCategory(category));
    }

    @Operation(summary = "热门工具", description = "获取安装次数最多的工具列表", tags = {"公开接口"})
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "成功返回热门工具列表"),
            @ApiResponse(responseCode = "401", description = "未认证", content = @Content)
    })
    @GetMapping("/tools/popular")
    public ResponseEntity<List<ToolMetadata>> getPopular(
            @Parameter(description = "返回数量上限", example = "10") @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(marketplace.getPopular(limit));
    }

    @Operation(summary = "高评分工具", description = "获取平均评分最高的工具列表", tags = {"公开接口"})
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "成功返回高评分工具列表"),
            @ApiResponse(responseCode = "401", description = "未认证", content = @Content)
    })
    @GetMapping("/tools/top-rated")
    public ResponseEntity<List<ToolMetadata>> getTopRated(
            @Parameter(description = "返回数量上限", example = "10") @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(marketplace.getTopRated(limit));
    }

    @Operation(summary = "获取工具详情", description = "获取指定工具的完整元数据，包括版本列表、评价等", tags = {"公开接口"})
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "成功返回工具详情"),
            @ApiResponse(responseCode = "404", description = "工具不存在", content = @Content),
            @ApiResponse(responseCode = "401", description = "未认证", content = @Content)
    })
    @GetMapping("/tools/{toolId}")
    public ResponseEntity<ToolMetadata> getDetail(
            @Parameter(description = "工具唯一 ID", required = true, example = "publisher:weather-tool") @PathVariable String toolId) {
        ToolMetadata detail = marketplace.getDetail(toolId);
        return detail != null ? ResponseEntity.ok(detail) : ResponseEntity.notFound().build();
    }

    @Operation(summary = "获取工具版本列表", description = "获取指定工具的所有可用版本", tags = {"公开接口"})
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "成功返回版本列表"),
            @ApiResponse(responseCode = "404", description = "工具不存在", content = @Content),
            @ApiResponse(responseCode = "401", description = "未认证", content = @Content)
    })
    @GetMapping("/tools/{toolId}/versions")
    public ResponseEntity<List<ToolVersion>> getVersions(
            @Parameter(description = "工具唯一 ID", required = true, example = "publisher:weather-tool") @PathVariable String toolId) {
        ToolMetadata detail = marketplace.getDetail(toolId);
        return detail != null ? ResponseEntity.ok(detail.getAvailableVersions())
                : ResponseEntity.notFound().build();
    }

    @Operation(summary = "获取工具健康状态", description = "获取指定工具的健康检查状态", tags = {"公开接口"})
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "成功返回健康状态"),
            @ApiResponse(responseCode = "401", description = "未认证", content = @Content)
    })
    @GetMapping("/tools/{toolId}/health")
    public ResponseEntity<ToolHealthChecker.HealthStatus> getHealth(
            @Parameter(description = "工具唯一 ID", required = true, example = "publisher:weather-tool") @PathVariable String toolId) {
        return ResponseEntity.ok(healthChecker.getStatus(toolId));
    }

    @Operation(summary = "获取工具评价列表", description = "获取指定工具的所有用户评价", tags = {"公开接口"})
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "成功返回评价列表"),
            @ApiResponse(responseCode = "401", description = "未认证", content = @Content)
    })
    @GetMapping("/tools/{toolId}/reviews")
    public ResponseEntity<List<ToolReview>> getReviews(
            @Parameter(description = "工具唯一 ID", required = true, example = "publisher:weather-tool") @PathVariable String toolId) {
        return ResponseEntity.ok(marketplace.getReviews(toolId));
    }

    // ========== PUBLISHER 接口 ==========

    @Operation(summary = "提交新工具", description = "提交一个新工具到市场，状态为 PENDING_REVIEW", tags = {"Publisher 接口"})
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "成功提交工具"),
            @ApiResponse(responseCode = "400", description = "请求参数无效", content = @Content),
            @ApiResponse(responseCode = "401", description = "未认证", content = @Content),
            @ApiResponse(responseCode = "403", description = "无 Publisher 权限", content = @Content)
    })
    @PostMapping("/tools")
    public ResponseEntity<ToolMetadata> submit(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "工具提交信息", required = true,
                    content = @Content(schema = @Schema(implementation = ToolSubmitRequest.class)))
            @RequestBody ToolSubmitRequest request) {
        // 注：submit(tool, metadata) 需要 Tool 实例，此接口暂作占位
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "安装工具", description = "从市场安装指定版本的工具到本地", tags = {"Publisher 接口"})
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "成功安装工具"),
            @ApiResponse(responseCode = "400", description = "安装失败（版本不兼容、依赖缺失等）", content = @Content),
            @ApiResponse(responseCode = "404", description = "工具不存在", content = @Content),
            @ApiResponse(responseCode = "401", description = "未认证", content = @Content),
            @ApiResponse(responseCode = "403", description = "无 Publisher 权限", content = @Content)
    })
    @PostMapping("/tools/{toolId}/install")
    public ResponseEntity<ToolMetadata> install(
            @Parameter(description = "工具唯一 ID", required = true, example = "publisher:weather-tool") @PathVariable String toolId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "安装版本信息", required = true)
            @RequestBody InstallRequest request) {
        return ResponseEntity.ok(installManager.install(toolId, request.getVersion()));
    }

    @Operation(summary = "卸载工具", description = "卸载已安装的工具", tags = {"Publisher 接口"})
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "成功卸载工具"),
            @ApiResponse(responseCode = "404", description = "工具未安装", content = @Content),
            @ApiResponse(responseCode = "401", description = "未认证", content = @Content),
            @ApiResponse(responseCode = "403", description = "无 Publisher 权限", content = @Content)
    })
    @DeleteMapping("/tools/{toolId}/install")
    public ResponseEntity<Void> uninstall(
            @Parameter(description = "工具唯一 ID", required = true, example = "publisher:weather-tool") @PathVariable String toolId) {
        installManager.uninstall(toolId);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "获取已安装工具列表", description = "获取当前 Agent 已安装的所有工具及其版本", tags = {"Publisher 接口"})
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "成功返回已安装工具列表"),
            @ApiResponse(responseCode = "401", description = "未认证", content = @Content),
            @ApiResponse(responseCode = "403", description = "无 Publisher 权限", content = @Content)
    })
    @GetMapping("/tools/installed")
    public ResponseEntity<Map<String, ToolVersion>> getInstalled() {
        return ResponseEntity.ok(installManager.getInstalledTools());
    }

    @Operation(summary = "检查工具更新", description = "检查已安装工具是否有新版本可用", tags = {"Publisher 接口"})
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "成功返回有更新的工具列表"),
            @ApiResponse(responseCode = "401", description = "未认证", content = @Content),
            @ApiResponse(responseCode = "403", description = "无 Publisher 权限", content = @Content)
    })
    @GetMapping("/tools/installed/updates")
    public ResponseEntity<List<ToolMetadata>> checkUpdates() {
        return ResponseEntity.ok(installManager.checkUpdates());
    }

    @Operation(summary = "升级工具", description = "将已安装的工具升级到指定版本", tags = {"Publisher 接口"})
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "成功升级工具"),
            @ApiResponse(responseCode = "400", description = "升级失败（版本不兼容等）", content = @Content),
            @ApiResponse(responseCode = "404", description = "工具未安装", content = @Content),
            @ApiResponse(responseCode = "401", description = "未认证", content = @Content),
            @ApiResponse(responseCode = "403", description = "无 Publisher 权限", content = @Content)
    })
    @PostMapping("/tools/{toolId}/upgrade")
    public ResponseEntity<ToolMetadata> upgrade(
            @Parameter(description = "工具唯一 ID", required = true, example = "publisher:weather-tool") @PathVariable String toolId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "目标版本信息", required = true)
            @RequestBody UpgradeRequest request) {
        return ResponseEntity.ok(installManager.upgrade(toolId, request.getVersion()));
    }

    @Operation(summary = "添加工具评价", description = "对已安装的工具添加评分和评论", tags = {"Publisher 接口"})
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "成功添加评价"),
            @ApiResponse(responseCode = "400", description = "评分无效（需在 1-5 之间）", content = @Content),
            @ApiResponse(responseCode = "404", description = "工具不存在", content = @Content),
            @ApiResponse(responseCode = "401", description = "未认证", content = @Content),
            @ApiResponse(responseCode = "403", description = "无 Publisher 权限", content = @Content)
    })
    @PostMapping("/tools/{toolId}/reviews")
    public ResponseEntity<ToolReview> addReview(
            @Parameter(description = "工具唯一 ID", required = true, example = "publisher:weather-tool") @PathVariable String toolId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "评价内容", required = true)
            @RequestBody AddReviewRequest request) {
        ToolReview review = ToolReview.builder()
                .reviewId(UUID.randomUUID().toString())
                .toolId(toolId)
                .reviewer(request.getReviewer())
                .rating(request.getRating())
                .comment(request.getComment())
                .build();
        return ResponseEntity.ok(marketplace.addReview(toolId, review));
    }

    // ========== ADMIN 接口 ==========

    @Operation(summary = "审核工具", description = "管理员审核工具（通过/驳回），控制工具发布状态", tags = {"Admin 接口"})
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "审核完成"),
            @ApiResponse(responseCode = "400", description = "非法状态转换", content = @Content),
            @ApiResponse(responseCode = "404", description = "工具不存在", content = @Content),
            @ApiResponse(responseCode = "401", description = "未认证", content = @Content),
            @ApiResponse(responseCode = "403", description = "无 Admin 权限", content = @Content)
    })
    @PostMapping("/tools/{toolId}/review")
    public ResponseEntity<ToolMetadata> review(
            @Parameter(description = "工具唯一 ID", required = true, example = "publisher:weather-tool") @PathVariable String toolId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "审核决定", required = true)
            @RequestBody ReviewRequest request) {
        return ResponseEntity.ok(marketplace.review(toolId, request.getVersion(),
                request.isApproved(), request.getReason()));
    }

    @Operation(summary = "下架工具", description = "管理员下架工具，将状态设为 DEPRECATED", tags = {"Admin 接口"})
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "成功下架工具"),
            @ApiResponse(responseCode = "400", description = "非法状态转换", content = @Content),
            @ApiResponse(responseCode = "404", description = "工具不存在", content = @Content),
            @ApiResponse(responseCode = "401", description = "未认证", content = @Content),
            @ApiResponse(responseCode = "403", description = "无 Admin 权限", content = @Content)
    })
    @DeleteMapping("/tools/{toolId}")
    public ResponseEntity<Void> deprecate(
            @Parameter(description = "工具唯一 ID", required = true, example = "publisher:weather-tool") @PathVariable String toolId) {
        marketplace.deprecate(toolId);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "获取分类列表", description = "获取所有工具分类（含内置分类和自定义分类）", tags = {"Admin 接口"})
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "成功返回分类列表"),
            @ApiResponse(responseCode = "401", description = "未认证", content = @Content),
            @ApiResponse(responseCode = "403", description = "无 Admin 权限", content = @Content)
    })
    @GetMapping("/categories")
    public ResponseEntity<Collection<CategoryRegistry.Category>> getCategories() {
        return ResponseEntity.ok(categoryRegistry.getAll());
    }

    @Operation(summary = "添加分类", description = "添加自定义工具分类", tags = {"Admin 接口"})
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "成功添加分类"),
            @ApiResponse(responseCode = "400", description = "分类 key 已存在", content = @Content),
            @ApiResponse(responseCode = "401", description = "未认证", content = @Content),
            @ApiResponse(responseCode = "403", description = "无 Admin 权限", content = @Content)
    })
    @PostMapping("/categories")
    public ResponseEntity<Void> addCategory(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "分类信息", required = true)
            @RequestBody AddCategoryRequest request) {
        categoryRegistry.register(request.getKey(), request.getDisplayName(), request.getDescription());
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "获取所有工具健康状态", description = "获取所有已发布工具的健康检查状态", tags = {"Admin 接口"})
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "成功返回健康状态映射"),
            @ApiResponse(responseCode = "401", description = "未认证", content = @Content),
            @ApiResponse(responseCode = "403", description = "无 Admin 权限", content = @Content)
    })
    @GetMapping("/tools/health")
    public ResponseEntity<Map<String, ToolHealthChecker.HealthStatus>> getAllHealth() {
        Map<String, ToolHealthChecker.HealthStatus> result = new HashMap<>();
        marketplace.listPublished().forEach(m ->
                result.put(m.getToolId(), healthChecker.getStatus(m.getToolId())));
        return ResponseEntity.ok(result);
    }

    // ========== 请求体 ==========

    @lombok.Data
    @Schema(description = "安装请求")
    public static class InstallRequest {
        @Schema(description = "目标版本号", requiredMode = Schema.RequiredMode.REQUIRED, example = "{\"major\":1,\"minor\":0,\"patch\":0}")
        private ToolVersion version;
    }

    @lombok.Data
    @Schema(description = "审核请求")
    public static class ReviewRequest {
        @Schema(description = "审核的版本号", requiredMode = Schema.RequiredMode.REQUIRED)
        private ToolVersion version;
        @Schema(description = "是否通过审核", example = "true")
        private boolean approved;
        @Schema(description = "审核意见（驳回时必填）", example = "输入 Schema 不完整，请补充参数说明")
        private String reason;
    }

    @lombok.Data
    @Schema(description = "升级请求")
    public static class UpgradeRequest {
        @Schema(description = "目标版本号", requiredMode = Schema.RequiredMode.REQUIRED, example = "{\"major\":1,\"minor\":2,\"patch\":0}")
        private ToolVersion version;
    }

    @lombok.Data
    @Schema(description = "添加评价请求")
    public static class AddReviewRequest {
        @Schema(description = "评价者 ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "agent-weather")
        private String reviewer;
        @Schema(description = "评分（1-5）", requiredMode = Schema.RequiredMode.REQUIRED, example = "4", minimum = "1", maximum = "5")
        private int rating;
        @Schema(description = "评价内容", example = "非常好用的天气工具，准确率高")
        private String comment;
    }

    @lombok.Data
    @Schema(description = "添加分类请求")
    public static class AddCategoryRequest {
        @Schema(description = "分类唯一标识", requiredMode = Schema.RequiredMode.REQUIRED, example = "AI_GENERATION")
        private String key;
        @Schema(description = "分类显示名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "AI 生成")
        private String displayName;
        @Schema(description = "分类描述", example = "AI 文本/图片/视频生成相关工具")
        private String description;
    }

    @lombok.Data
    @Schema(description = "工具提交请求")
    public static class ToolSubmitRequest {
        @Schema(description = "工具唯一 ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "publisher:weather-tool")
        private String toolId;
        @Schema(description = "工具名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "天气查询工具")
        private String name;
        @Schema(description = "工具描述", example = "查询全球城市的天气信息，支持实时天气和预报")
        private String description;
        @Schema(description = "工具分类", example = "NETWORK")
        private String category;
        @Schema(description = "标签列表", example = "[\"weather\", \"api\", \"real-time\"]")
        private List<String> tags;
        @Schema(description = "版本信息", requiredMode = Schema.RequiredMode.REQUIRED)
        private ToolVersion version;
        @Schema(description = "输入 Schema（JSON Schema 格式）")
        private Map<String, Object> inputSchema;
        @Schema(description = "输出 Schema（JSON Schema 格式）")
        private Map<String, Object> outputSchema;
        @Schema(description = "Spring Bean 名称（LOCAL_BEAN 类型的工具）", example = "weatherTool")
        private String beanName;
    }
}
