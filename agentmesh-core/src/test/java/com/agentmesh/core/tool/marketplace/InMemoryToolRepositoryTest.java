package com.agentmesh.core.tool.marketplace;

import com.agentmesh.core.tool.marketplace.model.ToolMetadata;
import com.agentmesh.core.tool.marketplace.model.ToolVersion;
import com.agentmesh.core.tool.marketplace.repository.InMemoryToolRepository;
import com.agentmesh.core.tool.marketplace.repository.ToolRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryToolRepositoryTest {

    private InMemoryToolRepository repo;

    @BeforeEach
    void setUp() {
        repo = new InMemoryToolRepository();
    }

    private ToolMetadata createTool(String toolId, String name, ToolVersion version, ToolMetadata.ToolStatus status) {
        return ToolMetadata.builder()
                .toolId(toolId)
                .name(name)
                .description("Description for " + name)
                .category("DATA_PROCESSING")
                .tags(List.of("data", "test"))
                .publisher("pub1")
                .version(version)
                .status(status)
                .installCount(0)
                .averageRating(0.0)
                .reviewCount(0)
                .build();
    }

    @Test
    void shouldSaveAndFindById() {
        ToolMetadata meta = createTool("pub1:tool-a", "Tool A", ToolVersion.parse("1.0.0"), ToolMetadata.ToolStatus.PUBLISHED);
        repo.save(meta);

        Optional<ToolMetadata> found = repo.findById("pub1:tool-a");
        assertTrue(found.isPresent());
        assertEquals("Tool A", found.get().getName());
        assertEquals(ToolVersion.parse("1.0.0"), found.get().getVersion());
    }

    @Test
    void shouldSaveMultipleVersions() {
        repo.save(createTool("pub1:tool-a", "Tool A", ToolVersion.parse("1.0.0"), ToolMetadata.ToolStatus.PUBLISHED));
        repo.save(createTool("pub1:tool-a", "Tool A v2", ToolVersion.parse("2.0.0"), ToolMetadata.ToolStatus.PUBLISHED));

        Optional<ToolMetadata> latest = repo.findById("pub1:tool-a");
        assertTrue(latest.isPresent());
        assertEquals(ToolVersion.parse("2.0.0"), latest.get().getVersion());
        assertEquals("Tool A v2", latest.get().getName());

        List<ToolMetadata> allVersions = repo.findAllVersions("pub1:tool-a");
        assertEquals(2, allVersions.size());
    }

    @Test
    void shouldFindByIdAndVersion() {
        repo.save(createTool("pub1:tool-a", "Tool A v1", ToolVersion.parse("1.0.0"), ToolMetadata.ToolStatus.PUBLISHED));
        repo.save(createTool("pub1:tool-a", "Tool A v2", ToolVersion.parse("2.0.0"), ToolMetadata.ToolStatus.PUBLISHED));

        Optional<ToolMetadata> found = repo.findByIdAndVersion("pub1:tool-a", ToolVersion.parse("1.0.0"));
        assertTrue(found.isPresent());
        assertEquals("Tool A v1", found.get().getName());
    }

    @Test
    void shouldReturnEmptyForNonExistentTool() {
        assertTrue(repo.findById("nonexistent").isEmpty());
        assertTrue(repo.findByIdAndVersion("nonexistent", ToolVersion.parse("1.0.0")).isEmpty());
    }

    @Test
    void shouldFindAllPublished() {
        repo.save(createTool("pub1:tool-a", "Tool A", ToolVersion.parse("1.0.0"), ToolMetadata.ToolStatus.PUBLISHED));
        repo.save(createTool("pub1:tool-b", "Tool B", ToolVersion.parse("1.0.0"), ToolMetadata.ToolStatus.PENDING_REVIEW));
        repo.save(createTool("pub1:tool-c", "Tool C", ToolVersion.parse("1.0.0"), ToolMetadata.ToolStatus.PUBLISHED));

        List<ToolMetadata> published = repo.findAllPublished();
        assertEquals(2, published.size());
        assertTrue(published.stream().allMatch(m -> m.getStatus() == ToolMetadata.ToolStatus.PUBLISHED));
    }

    @Test
    void shouldFindByCategory() {
        repo.save(createTool("pub1:tool-a", "Tool A", ToolVersion.parse("1.0.0"), ToolMetadata.ToolStatus.PUBLISHED));
        repo.save(ToolMetadata.builder()
                .toolId("pub1:tool-b").name("Tool B").category("NETWORK")
                .version(ToolVersion.parse("1.0.0")).status(ToolMetadata.ToolStatus.PUBLISHED)
                .build());

        List<ToolMetadata> dataTools = repo.findByCategory("DATA_PROCESSING");
        assertEquals(1, dataTools.size());
        assertEquals("pub1:tool-a", dataTools.get(0).getToolId());
    }

    @Test
    void shouldFindByTag() {
        ToolMetadata meta = createTool("pub1:tool-a", "Tool A", ToolVersion.parse("1.0.0"), ToolMetadata.ToolStatus.PUBLISHED);
        repo.save(meta);

        List<ToolMetadata> found = repo.findByTag("data");
        assertEquals(1, found.size());
        assertEquals("pub1:tool-a", found.get(0).getToolId());

        assertTrue(repo.findByTag("nonexistent").isEmpty());
    }

    @Test
    void shouldFindByPublisher() {
        repo.save(createTool("pub1:tool-a", "Tool A", ToolVersion.parse("1.0.0"), ToolMetadata.ToolStatus.PUBLISHED));
        repo.save(ToolMetadata.builder()
                .toolId("pub2:tool-b").name("Tool B").publisher("pub2")
                .version(ToolVersion.parse("1.0.0")).status(ToolMetadata.ToolStatus.PUBLISHED)
                .build());

        List<ToolMetadata> pub1Tools = repo.findByPublisher("pub1");
        assertEquals(1, pub1Tools.size());
        assertEquals("pub1:tool-a", pub1Tools.get(0).getToolId());
    }

    @Test
    void shouldSearchByKeyword() {
        repo.save(createTool("pub1:web-scraper", "Web Scraper", ToolVersion.parse("1.0.0"), ToolMetadata.ToolStatus.PUBLISHED));
        repo.save(createTool("pub1:data-cleaner", "Data Cleaner", ToolVersion.parse("1.0.0"), ToolMetadata.ToolStatus.PUBLISHED));

        ToolRepository.SearchResult result = repo.search("web", 0, 10);
        assertEquals(1, result.total());
        assertEquals(1, result.items().size());
        assertEquals("pub1:web-scraper", result.items().get(0).getToolId());
    }

    @Test
    void shouldSearchByTagKeyword() {
        ToolMetadata meta = ToolMetadata.builder()
                .toolId("pub1:tool-a").name("Tool A").description("desc")
                .tags(List.of("web", "scraping")).category("DATA_PROCESSING")
                .version(ToolVersion.parse("1.0.0")).status(ToolMetadata.ToolStatus.PUBLISHED)
                .build();
        repo.save(meta);

        ToolRepository.SearchResult result = repo.search("scraping", 0, 10);
        assertEquals(1, result.total());
    }

    @Test
    void shouldSearchWithPagination() {
        for (int i = 0; i < 5; i++) {
            repo.save(createTool("pub1:tool-" + i, "Tool " + i, ToolVersion.parse("1.0.0"), ToolMetadata.ToolStatus.PUBLISHED));
        }

        ToolRepository.SearchResult page1 = repo.search("Tool", 0, 2);
        assertEquals(5, page1.total());
        assertEquals(2, page1.items().size());

        ToolRepository.SearchResult page2 = repo.search("Tool", 2, 2);
        assertEquals(5, page2.total());
        assertEquals(2, page2.items().size());
    }

    @Test
    void shouldDeleteById() {
        repo.save(createTool("pub1:tool-a", "Tool A", ToolVersion.parse("1.0.0"), ToolMetadata.ToolStatus.PUBLISHED));
        repo.deleteById("pub1:tool-a");
        assertTrue(repo.findById("pub1:tool-a").isEmpty());
    }

    @Test
    void shouldDeleteByVersion() {
        repo.save(createTool("pub1:tool-a", "Tool A v1", ToolVersion.parse("1.0.0"), ToolMetadata.ToolStatus.PUBLISHED));
        repo.save(createTool("pub1:tool-a", "Tool A v2", ToolVersion.parse("2.0.0"), ToolMetadata.ToolStatus.PUBLISHED));

        repo.deleteByIdAndVersion("pub1:tool-a", ToolVersion.parse("1.0.0"));
        assertTrue(repo.findByIdAndVersion("pub1:tool-a", ToolVersion.parse("1.0.0")).isEmpty());
        assertTrue(repo.findByIdAndVersion("pub1:tool-a", ToolVersion.parse("2.0.0")).isPresent());
    }

    @Test
    void shouldCountAllTools() {
        assertEquals(0, repo.count());
        repo.save(createTool("pub1:tool-a", "Tool A", ToolVersion.parse("1.0.0"), ToolMetadata.ToolStatus.PUBLISHED));
        assertEquals(1, repo.count());
        repo.save(createTool("pub1:tool-b", "Tool B", ToolVersion.parse("1.0.0"), ToolMetadata.ToolStatus.PUBLISHED));
        assertEquals(2, repo.count());
    }

    @Test
    void shouldReturnDefensiveCopy() {
        ToolMetadata meta = createTool("pub1:tool-a", "Tool A", ToolVersion.parse("1.0.0"), ToolMetadata.ToolStatus.PUBLISHED);
        repo.save(meta);

        ToolMetadata found = repo.findById("pub1:tool-a").orElseThrow();
        found.setName("Modified Name");

        ToolMetadata foundAgain = repo.findById("pub1:tool-a").orElseThrow();
        assertEquals("Tool A", foundAgain.getName());
    }

    @Test
    void shouldMaintainAvailableVersions() {
        repo.save(createTool("pub1:tool-a", "Tool A v1", ToolVersion.parse("1.0.0"), ToolMetadata.ToolStatus.PUBLISHED));
        repo.save(createTool("pub1:tool-a", "Tool A v2", ToolVersion.parse("2.0.0"), ToolMetadata.ToolStatus.PUBLISHED));

        ToolMetadata latest = repo.findById("pub1:tool-a").orElseThrow();
        assertNotNull(latest.getAvailableVersions());
        assertEquals(2, latest.getAvailableVersions().size());
    }
}