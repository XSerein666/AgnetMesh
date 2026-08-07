package com.agentmesh.core.tool.marketplace;

import com.agentmesh.core.tool.marketplace.model.ToolMetadata;
import com.agentmesh.core.tool.marketplace.model.ToolVersion;
import com.agentmesh.core.tool.marketplace.repository.ToolRepository;
import com.agentmesh.core.tool.marketplace.service.ToolSearchService;
import com.agentmesh.core.tool.marketplace.service.ToolSearchServiceImpl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ToolSearchServiceImplTest {

    @Mock
    private ToolRepository toolRepository;

    private ToolSearchServiceImpl searchService;

    @BeforeEach
    void setUp() {
        searchService = new ToolSearchServiceImpl(toolRepository);
    }

    private ToolMetadata createTool(String toolId, String name, String category) {
        return ToolMetadata.builder()
                .toolId(toolId).name(name).description("desc").category(category)
                .version(ToolVersion.parse("1.0.0")).status(ToolMetadata.ToolStatus.PUBLISHED)
                .build();
    }

    @Test
    void shouldSearchWithoutCategory() {
        when(toolRepository.search("test", 0, 20)).thenReturn(
                new ToolRepository.SearchResult(List.of(), 0));

        ToolRepository.SearchResult result = searchService.search(
                new ToolSearchService.SearchRequest("test", null, 0, 20));

        assertNotNull(result);
        verify(toolRepository).search("test", 0, 20);
    }

    @Test
    void shouldSearchWithCategory() {
        when(toolRepository.findAllPublished()).thenReturn(List.of(
                createTool("pub:tool-a", "Tool A", "DATA_PROCESSING"),
                createTool("pub:tool-b", "Tool B", "NETWORK"),
                createTool("pub:tool-c", "Tool C", "DATA_PROCESSING")
        ));

        ToolRepository.SearchResult result = searchService.search(
                new ToolSearchService.SearchRequest(null, "DATA_PROCESSING", 0, 20));

        assertEquals(2, result.total());
        assertTrue(result.items().stream().allMatch(m -> "DATA_PROCESSING".equals(m.getCategory())));
    }

    @Test
    void shouldSearchWithCategoryAndKeyword() {
        when(toolRepository.findAllPublished()).thenReturn(List.of(
                createTool("pub:tool-a", "Data Cleaner", "DATA_PROCESSING"),
                createTool("pub:tool-b", "Network Tool", "NETWORK"),
                createTool("pub:tool-c", "Data Transformer", "DATA_PROCESSING")
        ));

        ToolRepository.SearchResult result = searchService.search(
                new ToolSearchService.SearchRequest("Cleaner", "DATA_PROCESSING", 0, 20));

        assertEquals(1, result.total());
        assertEquals("pub:tool-a", result.items().get(0).getToolId());
    }

    @Test
    void shouldHandlePaginationInCategorySearch() {
        when(toolRepository.findAllPublished()).thenReturn(List.of(
                createTool("pub:tool-a", "Tool A", "DATA_PROCESSING"),
                createTool("pub:tool-b", "Tool B", "DATA_PROCESSING"),
                createTool("pub:tool-c", "Tool C", "DATA_PROCESSING")
        ));

        ToolRepository.SearchResult page1 = searchService.search(
                new ToolSearchService.SearchRequest(null, "DATA_PROCESSING", 0, 2));
        assertEquals(3, page1.total());
        assertEquals(2, page1.items().size());

        ToolRepository.SearchResult page2 = searchService.search(
                new ToolSearchService.SearchRequest(null, "DATA_PROCESSING", 1, 2));
        assertEquals(3, page2.total());
        assertEquals(1, page2.items().size());
    }

    @Test
    void shouldSearchByTagInCategory() {
        when(toolRepository.findAllPublished()).thenReturn(List.of(
                ToolMetadata.builder()
                        .toolId("pub:web-scraper").name("Web Scraper").category("NETWORK")
                        .tags(List.of("web", "scraping")).version(ToolVersion.parse("1.0.0"))
                        .status(ToolMetadata.ToolStatus.PUBLISHED).build()
        ));

        ToolRepository.SearchResult result = searchService.search(
                new ToolSearchService.SearchRequest("scraping", "NETWORK", 0, 20));

        assertEquals(1, result.total());
    }

    @Test
    void shouldReturnEmptyForNoMatch() {
        when(toolRepository.findAllPublished()).thenReturn(List.of(
                createTool("pub:tool-a", "Tool A", "DATA_PROCESSING")
        ));

        ToolRepository.SearchResult result = searchService.search(
                new ToolSearchService.SearchRequest("nonexistent", "DATA_PROCESSING", 0, 20));

        assertEquals(0, result.total());
        assertTrue(result.items().isEmpty());
    }
}