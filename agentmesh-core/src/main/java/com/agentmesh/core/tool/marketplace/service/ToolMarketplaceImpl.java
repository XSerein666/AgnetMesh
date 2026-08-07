package com.agentmesh.core.tool.marketplace.service;

import com.agentmesh.core.agent.AgentConfig;
import com.agentmesh.core.tool.Tool;
import com.agentmesh.core.tool.marketplace.exception.IllegalStateTransitionException;
import com.agentmesh.core.tool.marketplace.exception.InvalidReviewStateException;
import com.agentmesh.core.tool.marketplace.health.CategoryRegistry;
import com.agentmesh.core.tool.marketplace.health.ToolReviewPolicy;
import com.agentmesh.core.tool.marketplace.model.ToolExecutionDescriptor;
import com.agentmesh.core.tool.marketplace.model.ToolMetadata;
import com.agentmesh.core.tool.marketplace.model.ToolReview;
import com.agentmesh.core.tool.marketplace.model.ToolVersion;
import com.agentmesh.core.tool.marketplace.repository.ReviewRepository;
import com.agentmesh.core.tool.marketplace.repository.ToolRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 工具市场核心实现。
 *
 * 关键设计：
 * - submit() 调用 reviewPolicy 决定自动通过还是待审核
 * - review() 校验当前状态必须为 PENDING_REVIEW
 * - 评分聚合使用写锁保证一致性
 */
@Slf4j
public class ToolMarketplaceImpl implements ToolMarketplace {

    private final ToolRepository toolRepository;
    private final CategoryRegistry categoryRegistry;
    private final ToolReviewPolicy reviewPolicy;
    private final ReviewRepository reviewRepository;
    private final ApplicationContext applicationContext;
    private final AgentConfig agentConfig;
    private final ReentrantReadWriteLock reviewLock = new ReentrantReadWriteLock();

    public ToolMarketplaceImpl(ToolRepository toolRepository,
                                CategoryRegistry categoryRegistry,
                                ToolReviewPolicy reviewPolicy,
                                ReviewRepository reviewRepository,
                                ApplicationContext applicationContext,
                                AgentConfig agentConfig) {
        this.toolRepository = toolRepository;
        this.categoryRegistry = categoryRegistry;
        this.reviewPolicy = reviewPolicy;
        this.reviewRepository = reviewRepository;
        this.applicationContext = applicationContext;
        this.agentConfig = agentConfig;
    }

    private String getCurrentAgentId() {
        return agentConfig.getAgentId();
    }

    @Override
    public ToolMetadata submit(Tool<?, ?> tool, ToolMetadata metadata) {
        if (!categoryRegistry.exists(metadata.getCategory())) {
            throw new IllegalArgumentException("分类不存在: " + metadata.getCategory());
        }

        ToolExecutionDescriptor descriptor = ToolExecutionDescriptor.builder()
                .type(ToolExecutionDescriptor.ExecutionType.LOCAL_BEAN)
                .beanName(tool.getId())
                .timeoutMillis(30000)
                .build();

        metadata.setExecutionDescriptor(descriptor);
        metadata.setPublisher(getCurrentAgentId());
        metadata.setCreatedAt(Instant.now());
        metadata.setUpdatedAt(Instant.now());
        metadata.setInstallCount(0);
        metadata.setAverageRating(0.0);
        metadata.setReviewCount(0);

        List<ToolVersion> existingVersions = new ArrayList<>();
        Optional<ToolMetadata> existing = toolRepository.findById(metadata.getToolId());
        if (existing.isPresent() && existing.get().getAvailableVersions() != null) {
            existingVersions.addAll(existing.get().getAvailableVersions());
        }
        if (!existingVersions.contains(metadata.getVersion())) {
            existingVersions.add(metadata.getVersion());
        }
        metadata.setAvailableVersions(existingVersions);

        ToolReviewPolicy.ReviewResult result = reviewPolicy.review(metadata);
        ToolMetadata.ToolStatus targetStatus = result.isApproved()
                ? ToolMetadata.ToolStatus.PUBLISHED : ToolMetadata.ToolStatus.PENDING_REVIEW;

        if (existing.isPresent()) {
            ToolMetadata.ToolStatus currentStatus = existing.get().getStatus();
            if (currentStatus == ToolMetadata.ToolStatus.REJECTED && targetStatus == ToolMetadata.ToolStatus.PENDING_REVIEW) {
                log.info("[ToolMarketplace] 工具重新提交审核: {} v{}", metadata.getToolId(), metadata.getVersion());
            } else if (currentStatus == ToolMetadata.ToolStatus.PUBLISHED) {
                throw new InvalidReviewStateException(
                        "工具已发布，请通过版本更新流程提交新版本，而非重新提交", metadata.getToolId(), currentStatus);
            } else if (!currentStatus.canTransitionTo(targetStatus)) {
                throw new IllegalStateTransitionException(currentStatus.name(), targetStatus.name());
            }
        }

        metadata.setStatus(targetStatus);
        if (result.isApproved()) {
            log.info("[ToolMarketplace] 工具自动审核通过: {} v{}", metadata.getToolId(), metadata.getVersion());
        } else {
            log.info("[ToolMarketplace] 工具提交待审核: {} v{}, reason={}",
                    metadata.getToolId(), metadata.getVersion(), result.getReason());
        }

        toolRepository.save(metadata);
        return metadata;
    }

    @Override
    public ToolMetadata review(String toolId, ToolVersion version, boolean approved, String reason) {
        ToolMetadata metadata = getDetail(toolId, version);
        if (metadata == null) {
            throw new IllegalArgumentException("工具不存在: " + toolId
                    + (version != null ? " v" + version : ""));
        }

        if (metadata.getStatus() != ToolMetadata.ToolStatus.PENDING_REVIEW) {
            throw new InvalidReviewStateException(
                    "工具状态为 " + metadata.getStatus() + "，无法审核。只有 PENDING_REVIEW 状态的工具才能审核。",
                    toolId, metadata.getStatus());
        }

        ToolMetadata.ToolStatus targetStatus = approved
                ? ToolMetadata.ToolStatus.PUBLISHED : ToolMetadata.ToolStatus.REJECTED;
        if (!metadata.getStatus().canTransitionTo(targetStatus)) {
            throw new IllegalStateTransitionException(
                    metadata.getStatus().name(), targetStatus.name());
        }

        metadata.setStatus(targetStatus);
        metadata.setUpdatedAt(Instant.now());
        toolRepository.save(metadata);
        log.info("[ToolMarketplace] 审核完成: {} v{} → {}, reason={}",
                toolId, metadata.getVersion(), targetStatus, reason);
        return metadata;
    }

    @Override
    public void deprecate(String toolId) {
        ToolMetadata metadata = getDetail(toolId);
        if (metadata == null) {
            throw new IllegalArgumentException("工具不存在: " + toolId);
        }
        if (!metadata.getStatus().canTransitionTo(ToolMetadata.ToolStatus.DEPRECATED)) {
            throw new IllegalStateTransitionException(
                    metadata.getStatus().name(), ToolMetadata.ToolStatus.DEPRECATED.name());
        }
        metadata.setStatus(ToolMetadata.ToolStatus.DEPRECATED);
        metadata.setUpdatedAt(Instant.now());
        toolRepository.save(metadata);
        log.info("[ToolMarketplace] 下架工具: {}", toolId);
    }

    @Override
    public List<ToolMetadata> listPublished() {
        return toolRepository.findAllPublished();
    }

    @Override
    public List<ToolMetadata> browseByCategory(String category) {
        return toolRepository.findByCategory(category);
    }

    @Override
    public ToolRepository.SearchResult search(String keyword, int offset, int limit) {
        return toolRepository.search(keyword, offset, limit);
    }

    @Override
    public List<ToolMetadata> search(String keyword) {
        return toolRepository.search(keyword, 0, 20).items();
    }

    @Override
    public List<ToolMetadata> getPopular(int limit) {
        return toolRepository.findAllPublished().stream()
                .sorted((a, b) -> Integer.compare(
                        b.getInstallCount() != null ? b.getInstallCount() : 0,
                        a.getInstallCount() != null ? a.getInstallCount() : 0))
                .limit(limit)
                .toList();
    }

    @Override
    public List<ToolMetadata> getTopRated(int limit) {
        return toolRepository.findAllPublished().stream()
                .sorted((a, b) -> Double.compare(
                        b.getAverageRating() != null ? b.getAverageRating() : 0.0,
                        a.getAverageRating() != null ? a.getAverageRating() : 0.0))
                .limit(limit)
                .toList();
    }

    @Override
    public ToolMetadata getDetail(String toolId) {
        return toolRepository.findById(toolId).orElse(null);
    }

    @Override
    public ToolMetadata getDetail(String toolId, ToolVersion version) {
        return toolRepository.findByIdAndVersion(toolId, version).orElse(null);
    }

    @Override
    public ToolReview addReview(String toolId, ToolReview review) {
        reviewLock.writeLock().lock();
        try {
            if (review.getRating() < 1 || review.getRating() > 5) {
                throw new IllegalArgumentException("评分必须在 1-5 之间");
            }

            reviewRepository.save(review);

            List<ToolReview> allReviews = reviewRepository.findByToolId(toolId);
            double avg = allReviews.stream()
                    .mapToInt(ToolReview::getRating)
                    .average()
                    .orElse(0.0);

            toolRepository.findById(toolId).ifPresent(metadata -> {
                ToolMetadata updated = metadata.toBuilder()
                        .averageRating(Math.round(avg * 10.0) / 10.0)
                        .reviewCount(allReviews.size())
                        .updatedAt(Instant.now())
                        .build();
                toolRepository.save(updated);
            });

            return review;
        } finally {
            reviewLock.writeLock().unlock();
        }
    }

    @Override
    public List<ToolReview> getReviews(String toolId) {
        return reviewRepository.findByToolId(toolId);
    }
}
