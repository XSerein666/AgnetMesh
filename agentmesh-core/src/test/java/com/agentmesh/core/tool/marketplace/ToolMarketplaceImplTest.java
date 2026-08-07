package com.agentmesh.core.tool.marketplace;

import com.agentmesh.core.agent.AgentConfig;
import com.agentmesh.core.tool.Tool;
import com.agentmesh.core.tool.marketplace.exception.IllegalStateTransitionException;
import com.agentmesh.core.tool.marketplace.exception.InvalidReviewStateException;
import com.agentmesh.core.tool.marketplace.health.AutoApprovePolicy;
import com.agentmesh.core.tool.marketplace.health.CategoryRegistry;
import com.agentmesh.core.tool.marketplace.health.ToolReviewPolicy;
import com.agentmesh.core.tool.marketplace.model.ToolExecutionDescriptor;
import com.agentmesh.core.tool.marketplace.model.ToolMetadata;
import com.agentmesh.core.tool.marketplace.model.ToolReview;
import com.agentmesh.core.tool.marketplace.model.ToolVersion;
import com.agentmesh.core.tool.marketplace.repository.ReviewRepository;
import com.agentmesh.core.tool.marketplace.repository.ToolRepository;
import com.agentmesh.core.tool.marketplace.service.ToolMarketplaceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ToolMarketplaceImplTest {

    @Mock
    private ToolRepository toolRepository;
    @Mock
    private ReviewRepository reviewRepository;
    @Mock
    private ApplicationContext applicationContext;
    @Mock
    private AgentConfig agentConfig;
    @Mock
    private Tool<?, ?> mockTool;

    private CategoryRegistry categoryRegistry;
    private ToolReviewPolicy reviewPolicy;
    private ToolMarketplaceImpl marketplace;

    @BeforeEach
    void setUp() {
        categoryRegistry = new CategoryRegistry();
        reviewPolicy = new AutoApprovePolicy();
        lenient().when(agentConfig.getAgentId()).thenReturn("test-agent");
        lenient().when(mockTool.getId()).thenReturn("test-agent:tool-a");

        marketplace = new ToolMarketplaceImpl(
                toolRepository, categoryRegistry, reviewPolicy,
                reviewRepository, applicationContext, agentConfig);
    }

    private ToolMetadata createMetadata(String toolId, ToolVersion version) {
        return ToolMetadata.builder()
                .toolId(toolId)
                .name("Test Tool " + toolId)
                .description("A test tool")
                .category("DATA_PROCESSING")
                .version(version)
                .tags(List.of("test"))
                .build();
    }

    @Test
    void shouldSubmitAndAutoApprove() {
        ToolMetadata meta = createMetadata("test-agent:tool-a", ToolVersion.parse("1.0.0"));
        when(toolRepository.findById("test-agent:tool-a")).thenReturn(Optional.empty());

        ToolMetadata result = marketplace.submit(mockTool, meta);

        assertNotNull(result);
        assertEquals(ToolMetadata.ToolStatus.PUBLISHED, result.getStatus());
        assertEquals("test-agent", result.getPublisher());
        assertNotNull(result.getExecutionDescriptor());
        assertEquals(ToolExecutionDescriptor.ExecutionType.LOCAL_BEAN, result.getExecutionDescriptor().getType());
        verify(toolRepository).save(any(ToolMetadata.class));
    }

    @Test
    void shouldSubmitWithInvalidCategory() {
        ToolMetadata meta = ToolMetadata.builder()
                .toolId("test-agent:tool-a")
                .name("Tool")
                .description("desc")
                .category("INVALID_CATEGORY")
                .version(ToolVersion.parse("1.0.0"))
                .build();

        assertThrows(IllegalArgumentException.class, () -> marketplace.submit(mockTool, meta));
    }

    @Test
    void shouldReviewAndApprove() {
        ToolMetadata meta = createMetadata("test-agent:tool-a", ToolVersion.parse("1.0.0"));
        meta.setStatus(ToolMetadata.ToolStatus.PENDING_REVIEW);
        when(toolRepository.findByIdAndVersion("test-agent:tool-a", ToolVersion.parse("1.0.0")))
                .thenReturn(Optional.of(meta));

        ToolMetadata result = marketplace.review("test-agent:tool-a", ToolVersion.parse("1.0.0"), true, "Looks good");

        assertEquals(ToolMetadata.ToolStatus.PUBLISHED, result.getStatus());
        verify(toolRepository).save(any(ToolMetadata.class));
    }

    @Test
    void shouldReviewAndReject() {
        ToolMetadata meta = createMetadata("test-agent:tool-a", ToolVersion.parse("1.0.0"));
        meta.setStatus(ToolMetadata.ToolStatus.PENDING_REVIEW);
        when(toolRepository.findByIdAndVersion("test-agent:tool-a", ToolVersion.parse("1.0.0")))
                .thenReturn(Optional.of(meta));

        ToolMetadata result = marketplace.review("test-agent:tool-a", ToolVersion.parse("1.0.0"), false, "Needs improvement");

        assertEquals(ToolMetadata.ToolStatus.REJECTED, result.getStatus());
    }

    @Test
    void shouldRejectReviewOfNonPendingTool() {
        ToolMetadata meta = createMetadata("test-agent:tool-a", ToolVersion.parse("1.0.0"));
        meta.setStatus(ToolMetadata.ToolStatus.PUBLISHED);
        when(toolRepository.findByIdAndVersion("test-agent:tool-a", ToolVersion.parse("1.0.0")))
                .thenReturn(Optional.of(meta));

        assertThrows(InvalidReviewStateException.class,
                () -> marketplace.review("test-agent:tool-a", ToolVersion.parse("1.0.0"), true, "ok"));
    }

    @Test
    void shouldDeprecatePublishedTool() {
        ToolMetadata meta = createMetadata("test-agent:tool-a", ToolVersion.parse("1.0.0"));
        meta.setStatus(ToolMetadata.ToolStatus.PUBLISHED);
        when(toolRepository.findById("test-agent:tool-a")).thenReturn(Optional.of(meta));

        marketplace.deprecate("test-agent:tool-a");

        assertEquals(ToolMetadata.ToolStatus.DEPRECATED, meta.getStatus());
        verify(toolRepository).save(meta);
    }

    @Test
    void shouldRejectDeprecateOfNonPublishedTool() {
        ToolMetadata meta = createMetadata("test-agent:tool-a", ToolVersion.parse("1.0.0"));
        meta.setStatus(ToolMetadata.ToolStatus.PENDING_REVIEW);
        when(toolRepository.findById("test-agent:tool-a")).thenReturn(Optional.of(meta));

        assertThrows(IllegalStateTransitionException.class,
                () -> marketplace.deprecate("test-agent:tool-a"));
    }

    @Test
    void shouldListPublished() {
        when(toolRepository.findAllPublished()).thenReturn(List.of(
                createMetadata("test-agent:tool-a", ToolVersion.parse("1.0.0")),
                createMetadata("test-agent:tool-b", ToolVersion.parse("1.0.0"))
        ));

        List<ToolMetadata> result = marketplace.listPublished();
        assertEquals(2, result.size());
    }

    @Test
    void shouldBrowseByCategory() {
        when(toolRepository.findByCategory("DATA_PROCESSING")).thenReturn(List.of(
                createMetadata("test-agent:tool-a", ToolVersion.parse("1.0.0"))
        ));

        List<ToolMetadata> result = marketplace.browseByCategory("DATA_PROCESSING");
        assertEquals(1, result.size());
    }

    @Test
    void shouldSearch() {
        ToolRepository.SearchResult searchResult = new ToolRepository.SearchResult(
                List.of(createMetadata("test-agent:tool-a", ToolVersion.parse("1.0.0"))), 1);
        when(toolRepository.search("keyword", 0, 20)).thenReturn(searchResult);

        ToolRepository.SearchResult result = marketplace.search("keyword", 0, 20);
        assertEquals(1, result.total());
    }

    @Test
    void shouldGetPopular() {
        ToolMetadata high = ToolMetadata.builder()
                .toolId("test-agent:high").name("High").installCount(100)
                .status(ToolMetadata.ToolStatus.PUBLISHED).version(ToolVersion.parse("1.0.0"))
                .build();
        ToolMetadata low = ToolMetadata.builder()
                .toolId("test-agent:low").name("Low").installCount(10)
                .status(ToolMetadata.ToolStatus.PUBLISHED).version(ToolVersion.parse("1.0.0"))
                .build();
        when(toolRepository.findAllPublished()).thenReturn(List.of(high, low));

        List<ToolMetadata> popular = marketplace.getPopular(1);
        assertEquals(1, popular.size());
        assertEquals("test-agent:high", popular.get(0).getToolId());
    }

    @Test
    void shouldGetTopRated() {
        ToolMetadata high = ToolMetadata.builder()
                .toolId("test-agent:high").name("High").averageRating(4.8)
                .status(ToolMetadata.ToolStatus.PUBLISHED).version(ToolVersion.parse("1.0.0"))
                .build();
        ToolMetadata low = ToolMetadata.builder()
                .toolId("test-agent:low").name("Low").averageRating(2.0)
                .status(ToolMetadata.ToolStatus.PUBLISHED).version(ToolVersion.parse("1.0.0"))
                .build();
        when(toolRepository.findAllPublished()).thenReturn(List.of(high, low));

        List<ToolMetadata> topRated = marketplace.getTopRated(1);
        assertEquals(1, topRated.size());
        assertEquals("test-agent:high", topRated.get(0).getToolId());
    }

    @Test
    void shouldGetDetail() {
        ToolMetadata meta = createMetadata("test-agent:tool-a", ToolVersion.parse("1.0.0"));
        when(toolRepository.findById("test-agent:tool-a")).thenReturn(Optional.of(meta));

        ToolMetadata result = marketplace.getDetail("test-agent:tool-a");
        assertNotNull(result);
        assertEquals("test-agent:tool-a", result.getToolId());
    }

    @Test
    void shouldReturnNullForNonExistentDetail() {
        when(toolRepository.findById("nonexistent")).thenReturn(Optional.empty());
        assertNull(marketplace.getDetail("nonexistent"));
    }

    @Test
    void shouldAddReview() {
        ToolMetadata meta = createMetadata("test-agent:tool-a", ToolVersion.parse("1.0.0"));
        meta.setStatus(ToolMetadata.ToolStatus.PUBLISHED);
        when(toolRepository.findById("test-agent:tool-a")).thenReturn(Optional.of(meta));

        ToolReview review = ToolReview.builder()
                .reviewId("r1").toolId("test-agent:tool-a")
                .reviewer("user1").rating(4).comment("Nice!")
                .build();

        ToolReview result = marketplace.addReview("test-agent:tool-a", review);

        assertNotNull(result);
        assertEquals(4, result.getRating());
        verify(reviewRepository).save(review);
        verify(toolRepository).save(any(ToolMetadata.class));
    }

    @Test
    void shouldRejectInvalidRating() {
        ToolReview review = ToolReview.builder()
                .reviewId("r1").toolId("test-agent:tool-a")
                .reviewer("user1").rating(0).comment("Bad")
                .build();

        assertThrows(IllegalArgumentException.class,
                () -> marketplace.addReview("test-agent:tool-a", review));
    }

    @Test
    void shouldGetReviews() {
        when(reviewRepository.findByToolId("test-agent:tool-a")).thenReturn(List.of(
                ToolReview.builder().reviewId("r1").toolId("test-agent:tool-a").reviewer("u1").rating(5).build()
        ));

        List<ToolReview> reviews = marketplace.getReviews("test-agent:tool-a");
        assertEquals(1, reviews.size());
    }
}