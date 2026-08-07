package com.agentmesh.core.tool.marketplace;

import com.agentmesh.core.tool.marketplace.model.ToolReview;
import com.agentmesh.core.tool.marketplace.repository.InMemoryReviewRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryReviewRepositoryTest {

    private InMemoryReviewRepository repo;

    @BeforeEach
    void setUp() {
        repo = new InMemoryReviewRepository();
    }

    private ToolReview createReview(String toolId, String reviewer, int rating) {
        return ToolReview.builder()
                .reviewId(UUID.randomUUID().toString())
                .toolId(toolId)
                .reviewer(reviewer)
                .rating(rating)
                .comment("Good tool")
                .build();
    }

    @Test
    void shouldSaveAndFindByToolId() {
        repo.save(createReview("pub1:tool-a", "user1", 5));
        repo.save(createReview("pub1:tool-a", "user2", 4));

        List<ToolReview> reviews = repo.findByToolId("pub1:tool-a");
        assertEquals(2, reviews.size());
    }

    @Test
    void shouldReturnEmptyListForNoReviews() {
        assertTrue(repo.findByToolId("nonexistent").isEmpty());
    }

    @Test
    void shouldFindByReviewer() {
        repo.save(createReview("pub1:tool-a", "user1", 5));
        repo.save(createReview("pub1:tool-b", "user1", 3));
        repo.save(createReview("pub1:tool-a", "user2", 4));

        List<ToolReview> user1Reviews = repo.findByReviewer("user1");
        assertEquals(2, user1Reviews.size());
    }

    @Test
    void shouldDeleteById() {
        ToolReview review = createReview("pub1:tool-a", "user1", 5);
        repo.save(review);
        assertEquals(1, repo.findByToolId("pub1:tool-a").size());

        repo.deleteById(review.getReviewId());
        assertTrue(repo.findByToolId("pub1:tool-a").isEmpty());
    }

    @Test
    void shouldCountByToolId() {
        assertEquals(0, repo.countByToolId("pub1:tool-a"));

        repo.save(createReview("pub1:tool-a", "user1", 5));
        repo.save(createReview("pub1:tool-a", "user2", 4));
        assertEquals(2, repo.countByToolId("pub1:tool-a"));
    }

    @Test
    void shouldReturnImmutableList() {
        repo.save(createReview("pub1:tool-a", "user1", 5));
        List<ToolReview> reviews = repo.findByToolId("pub1:tool-a");
        assertThrows(UnsupportedOperationException.class, () -> reviews.clear());
    }
}