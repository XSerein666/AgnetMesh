package com.agentmesh.core.tool.marketplace;

import com.agentmesh.core.tool.marketplace.model.ToolMetadata;
import com.agentmesh.core.tool.marketplace.model.ToolVersion;
import com.agentmesh.core.tool.marketplace.repository.ToolRepository;
import com.agentmesh.core.tool.marketplace.service.SimpleToolRecommendService;
import com.agentmesh.core.tool.marketplace.service.ToolMarketplace;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SimpleToolRecommendServiceTest {

    @Mock
    private ToolMarketplace marketplace;
    @Mock
    private ToolRepository toolRepository;

    private SimpleToolRecommendService recommendService;

    @BeforeEach
    void setUp() {
        recommendService = new SimpleToolRecommendService(marketplace, toolRepository);
    }

    private ToolMetadata createTool(String toolId, String category, int installCount, double rating) {
        return ToolMetadata.builder()
                .toolId(toolId).name("Tool " + toolId)
                .category(category).version(ToolVersion.parse("1.0.0"))
                .status(ToolMetadata.ToolStatus.PUBLISHED)
                .installCount(installCount).averageRating(rating)
                .build();
    }

    @Test
    void shouldRecommendByInstallHistory() {
        ToolMetadata installed = createTool("pub:data-tool", "DATA_PROCESSING", 100, 4.5);
        when(toolRepository.findById("pub:data-tool")).thenReturn(Optional.of(installed));

        ToolMetadata candidate1 = createTool("pub:data-tool-2", "DATA_PROCESSING", 50, 4.0);
        ToolMetadata candidate2 = createTool("pub:net-tool", "NETWORK", 200, 3.0);
        when(toolRepository.findAllPublished()).thenReturn(List.of(candidate1, candidate2));

        recommendService.recordInstall("agent1", "pub:data-tool");

        List<ToolMetadata> result = recommendService.recommend("agent1", 5);
        assertFalse(result.isEmpty());
        assertEquals("pub:data-tool-2", result.get(0).getToolId());
    }

    @Test
    void shouldNotRecommendAlreadyInstalledTools() {
        ToolMetadata installed = createTool("pub:tool-a", "DATA_PROCESSING", 100, 4.5);
        when(toolRepository.findById("pub:tool-a")).thenReturn(Optional.of(installed));

        when(toolRepository.findAllPublished()).thenReturn(List.of(installed));

        recommendService.recordInstall("agent1", "pub:tool-a");

        List<ToolMetadata> result = recommendService.recommend("agent1", 5);
        assertTrue(result.isEmpty());
    }

    @Test
    void shouldRecommendSimilar() {
        ToolMetadata target = createTool("pub:tool-a", "DATA_PROCESSING", 100, 4.5);
        when(toolRepository.findById("pub:tool-a")).thenReturn(Optional.of(target));

        ToolMetadata similar = createTool("pub:tool-b", "DATA_PROCESSING", 50, 4.0);
        ToolMetadata different = createTool("pub:tool-c", "NETWORK", 200, 3.0);
        when(toolRepository.findAllPublished()).thenReturn(List.of(similar, different));

        List<ToolMetadata> result = recommendService.recommendSimilar("pub:tool-a", 5);
        assertEquals(1, result.size());
        assertEquals("pub:tool-b", result.get(0).getToolId());
    }

    @Test
    void shouldReturnEmptyForNonExistentSimilar() {
        when(toolRepository.findById("nonexistent")).thenReturn(Optional.empty());
        assertTrue(recommendService.recommendSimilar("nonexistent", 5).isEmpty());
    }

    @Test
    void shouldRecommendByRole() {
        ToolMetadata popular = createTool("pub:tool-a", "DATA_PROCESSING", 100, 4.5);
        ToolMetadata lessPopular = createTool("pub:tool-b", "NETWORK", 10, 3.0);
        when(toolRepository.findAllPublished()).thenReturn(List.of(popular, lessPopular));

        List<ToolMetadata> result = recommendService.recommendByRole("developer", 1);
        assertEquals(1, result.size());
        assertEquals("pub:tool-a", result.get(0).getToolId());
    }

    @Test
    void shouldRecordView() {
        recommendService.recordView("agent1", "pub:tool-a");
        recommendService.recordView("agent1", "pub:tool-b");

        verifyNoInteractions(toolRepository);
    }
}