package com.agentmesh.core.tool.marketplace;

import com.agentmesh.core.tool.marketplace.api.ToolMarketplaceController;
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
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class ToolMarketplaceControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper = new ObjectMapper();

    private ToolMarketplace marketplace = mock(ToolMarketplace.class);
    private ToolSearchService searchService = mock(ToolSearchService.class);
    private ToolInstallManager installManager = mock(ToolInstallManager.class);
    private ToolHealthChecker healthChecker = mock(ToolHealthChecker.class);
    private ToolRecommendService recommendService = mock(ToolRecommendService.class);
    private CategoryRegistry categoryRegistry = new CategoryRegistry();

    @BeforeEach
    void setUp() {
        objectMapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        ToolMarketplaceController controller = new ToolMarketplaceController(
                marketplace, searchService, installManager, healthChecker,
                recommendService, categoryRegistry);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void shouldListPublished() throws Exception {
        when(marketplace.listPublished()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/tool-marketplace/tools")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldSearch() throws Exception {
        when(searchService.search(any())).thenReturn(
                new ToolRepository.SearchResult(List.of(), 0));

        mockMvc.perform(get("/api/v1/tool-marketplace/tools/search")
                        .param("q", "test")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldBrowseByCategory() throws Exception {
        when(marketplace.browseByCategory("DATA_PROCESSING")).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/tool-marketplace/tools/category/DATA_PROCESSING"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldGetPopular() throws Exception {
        when(marketplace.getPopular(10)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/tool-marketplace/tools/popular")
                        .param("limit", "10"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldGetTopRated() throws Exception {
        when(marketplace.getTopRated(10)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/tool-marketplace/tools/top-rated")
                        .param("limit", "10"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldGetDetail() throws Exception {
        when(marketplace.getDetail("pub:tool-a")).thenReturn(null);

        mockMvc.perform(get("/api/v1/tool-marketplace/tools/pub:tool-a"))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldGetDetailWhenExists() throws Exception {
        ToolMetadata meta = ToolMetadata.builder()
                .toolId("pub:tool-a").name("Tool A").version(ToolVersion.parse("1.0.0"))
                .status(ToolMetadata.ToolStatus.PUBLISHED).build();
        when(marketplace.getDetail("pub:tool-a")).thenReturn(meta);

        mockMvc.perform(get("/api/v1/tool-marketplace/tools/pub:tool-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.toolId").value("pub:tool-a"));
    }

    @Test
    void shouldGetVersions() throws Exception {
        ToolMetadata meta = ToolMetadata.builder()
                .toolId("pub:tool-a").name("Tool A").version(ToolVersion.parse("1.0.0"))
                .status(ToolMetadata.ToolStatus.PUBLISHED)
                .availableVersions(List.of(ToolVersion.parse("1.0.0"), ToolVersion.parse("2.0.0")))
                .build();
        when(marketplace.getDetail("pub:tool-a")).thenReturn(meta);

        mockMvc.perform(get("/api/v1/tool-marketplace/tools/pub:tool-a/versions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void shouldGetHealth() throws Exception {
        when(healthChecker.getStatus("pub:tool-a")).thenReturn(ToolHealthChecker.HealthStatus.HEALTHY);

        mockMvc.perform(get("/api/v1/tool-marketplace/tools/pub:tool-a/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value("HEALTHY"));
    }

    @Test
    void shouldGetReviews() throws Exception {
        when(marketplace.getReviews("pub:tool-a")).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/tool-marketplace/tools/pub:tool-a/reviews"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldInstallTool() throws Exception {
        ToolMetadata meta = ToolMetadata.builder()
                .toolId("pub:tool-a").name("Tool A").version(ToolVersion.parse("1.0.0"))
                .status(ToolMetadata.ToolStatus.PUBLISHED).build();
        when(installManager.install(eq("pub:tool-a"), any())).thenReturn(meta);

        mockMvc.perform(post("/api/v1/tool-marketplace/tools/pub:tool-a/install")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":{\"major\":1,\"minor\":0,\"patch\":0}}"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldUninstallTool() throws Exception {
        mockMvc.perform(delete("/api/v1/tool-marketplace/tools/pub:tool-a/install"))
                .andExpect(status().isOk());

        verify(installManager).uninstall("pub:tool-a");
    }

    @Test
    void shouldGetInstalled() throws Exception {
        when(installManager.getInstalledTools()).thenReturn(Map.of());

        mockMvc.perform(get("/api/v1/tool-marketplace/tools/installed"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldCheckUpdates() throws Exception {
        when(installManager.checkUpdates()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/tool-marketplace/tools/installed/updates"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldAddReview() throws Exception {
        when(marketplace.addReview(eq("pub:tool-a"), any())).thenReturn(
                ToolReview.builder().reviewId("r1").toolId("pub:tool-a").reviewer("user1").rating(5).build());

        mockMvc.perform(post("/api/v1/tool-marketplace/tools/pub:tool-a/reviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reviewer\":\"user1\",\"rating\":5,\"comment\":\"Great!\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldGetCategories() throws Exception {
        mockMvc.perform(get("/api/v1/tool-marketplace/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(9));
    }

    @Test
    void shouldAddCategory() throws Exception {
        mockMvc.perform(post("/api/v1/tool-marketplace/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\":\"CUSTOM\",\"displayName\":\"Custom\",\"description\":\"Custom category\"}"))
                .andExpect(status().isOk());

        assertTrue(categoryRegistry.exists("CUSTOM"));
    }
}