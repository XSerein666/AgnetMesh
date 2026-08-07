package com.agentmesh.core.tool.marketplace;

import com.agentmesh.core.tool.marketplace.model.ToolReview;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class ToolReviewTest {

    @Test
    void shouldBuildReview() {
        Instant now = Instant.now();
        ToolReview review = ToolReview.builder()
                .reviewId("r1")
                .toolId("pub:tool-a")
                .reviewer("user1")
                .rating(5)
                .comment("Excellent!")
                .createdAt(now)
                .build();

        assertEquals("r1", review.getReviewId());
        assertEquals("pub:tool-a", review.getToolId());
        assertEquals("user1", review.getReviewer());
        assertEquals(5, review.getRating());
        assertEquals("Excellent!", review.getComment());
        assertEquals(now, review.getCreatedAt());
    }

    @Test
    void shouldDefaultCreatedAt() {
        ToolReview review = ToolReview.builder()
                .reviewId("r1").toolId("pub:tool-a").reviewer("user1").rating(3)
                .build();

        assertNotNull(review.getCreatedAt());
    }
}