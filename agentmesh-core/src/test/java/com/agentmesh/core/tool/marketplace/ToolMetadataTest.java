package com.agentmesh.core.tool.marketplace;

import com.agentmesh.core.tool.marketplace.model.ToolMetadata;
import com.agentmesh.core.tool.marketplace.model.ToolVersion;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ToolMetadataTest {

    @Test
    void shouldBuildWithDefaults() {
        ToolMetadata meta = ToolMetadata.builder()
                .toolId("pub:test-tool")
                .name("Test Tool")
                .build();
        assertNotNull(meta.getCreatedAt());
        assertNotNull(meta.getUpdatedAt());
        assertEquals("pub:test-tool", meta.getToolId());
        assertEquals("Test Tool", meta.getName());
    }

    @Test
    void shouldBuildCompleteMetadata() {
        ToolVersion v = ToolVersion.parse("1.0.0");
        ToolMetadata meta = ToolMetadata.builder()
                .toolId("pub:my-tool")
                .name("My Tool")
                .description("A useful tool")
                .category("DATA_PROCESSING")
                .tags(List.of("data", "transform"))
                .publisher("pub")
                .version(v)
                .status(ToolMetadata.ToolStatus.PUBLISHED)
                .averageRating(4.5)
                .reviewCount(10)
                .installCount(100)
                .dependencies(List.of("pub:dep1"))
                .usageExample("example usage")
                .build();

        assertEquals("pub:my-tool", meta.getToolId());
        assertEquals("My Tool", meta.getName());
        assertEquals("A useful tool", meta.getDescription());
        assertEquals("DATA_PROCESSING", meta.getCategory());
        assertEquals(List.of("data", "transform"), meta.getTags());
        assertEquals("pub", meta.getPublisher());
        assertEquals(v, meta.getVersion());
        assertEquals(ToolMetadata.ToolStatus.PUBLISHED, meta.getStatus());
        assertEquals(4.5, meta.getAverageRating());
        assertEquals(10, meta.getReviewCount());
        assertEquals(100, meta.getInstallCount());
        assertEquals(List.of("pub:dep1"), meta.getDependencies());
        assertEquals("example usage", meta.getUsageExample());
    }

    @Test
    void shouldCreateCopyViaToBuilder() {
        ToolMetadata original = ToolMetadata.builder()
                .toolId("pub:tool")
                .name("Original")
                .status(ToolMetadata.ToolStatus.PUBLISHED)
                .installCount(5)
                .build();

        ToolMetadata copy = original.toBuilder()
                .name("Copy")
                .installCount(6)
                .build();

        assertEquals("pub:tool", copy.getToolId());
        assertEquals("Copy", copy.getName());
        assertEquals(6, copy.getInstallCount());
        assertEquals(ToolMetadata.ToolStatus.PUBLISHED, copy.getStatus());
    }

    // ========== 状态机测试 ==========

    @Test
    void pendingReviewCanTransitionToPublished() {
        assertTrue(ToolMetadata.ToolStatus.PENDING_REVIEW.canTransitionTo(ToolMetadata.ToolStatus.PUBLISHED));
    }

    @Test
    void pendingReviewCanTransitionToRejected() {
        assertTrue(ToolMetadata.ToolStatus.PENDING_REVIEW.canTransitionTo(ToolMetadata.ToolStatus.REJECTED));
    }

    @Test
    void pendingReviewCannotTransitionToDeprecated() {
        assertFalse(ToolMetadata.ToolStatus.PENDING_REVIEW.canTransitionTo(ToolMetadata.ToolStatus.DEPRECATED));
    }

    @Test
    void pendingReviewCannotTransitionToSelf() {
        assertFalse(ToolMetadata.ToolStatus.PENDING_REVIEW.canTransitionTo(ToolMetadata.ToolStatus.PENDING_REVIEW));
    }

    @Test
    void rejectedCanTransitionToPendingReview() {
        assertTrue(ToolMetadata.ToolStatus.REJECTED.canTransitionTo(ToolMetadata.ToolStatus.PENDING_REVIEW));
    }

    @Test
    void rejectedCannotTransitionToPublished() {
        assertFalse(ToolMetadata.ToolStatus.REJECTED.canTransitionTo(ToolMetadata.ToolStatus.PUBLISHED));
    }

    @Test
    void rejectedCannotTransitionToDeprecated() {
        assertFalse(ToolMetadata.ToolStatus.REJECTED.canTransitionTo(ToolMetadata.ToolStatus.DEPRECATED));
    }

    @Test
    void publishedCanTransitionToDeprecated() {
        assertTrue(ToolMetadata.ToolStatus.PUBLISHED.canTransitionTo(ToolMetadata.ToolStatus.DEPRECATED));
    }

    @Test
    void publishedCannotTransitionToPendingReview() {
        assertFalse(ToolMetadata.ToolStatus.PUBLISHED.canTransitionTo(ToolMetadata.ToolStatus.PENDING_REVIEW));
    }

    @Test
    void publishedCannotTransitionToRejected() {
        assertFalse(ToolMetadata.ToolStatus.PUBLISHED.canTransitionTo(ToolMetadata.ToolStatus.REJECTED));
    }

    @Test
    void deprecatedCanTransitionToPublished() {
        assertTrue(ToolMetadata.ToolStatus.DEPRECATED.canTransitionTo(ToolMetadata.ToolStatus.PUBLISHED));
    }

    @Test
    void deprecatedCannotTransitionToPendingReview() {
        assertFalse(ToolMetadata.ToolStatus.DEPRECATED.canTransitionTo(ToolMetadata.ToolStatus.PENDING_REVIEW));
    }

    @Test
    void deprecatedCannotTransitionToRejected() {
        assertFalse(ToolMetadata.ToolStatus.DEPRECATED.canTransitionTo(ToolMetadata.ToolStatus.REJECTED));
    }
}