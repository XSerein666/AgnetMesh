package com.agentmesh.core.tool.marketplace;

import com.agentmesh.core.tool.marketplace.health.AutoApprovePolicy;
import com.agentmesh.core.tool.marketplace.health.ToolReviewPolicy;
import com.agentmesh.core.tool.marketplace.model.ToolMetadata;
import com.agentmesh.core.tool.marketplace.model.ToolVersion;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AutoApprovePolicyTest {

    private AutoApprovePolicy policy;

    @BeforeEach
    void setUp() {
        policy = new AutoApprovePolicy();
    }

    @Test
    void shouldApproveValidMetadata() {
        ToolMetadata meta = ToolMetadata.builder()
                .toolId("pub:tool")
                .name("Valid Tool")
                .description("A good tool")
                .category("DATA_PROCESSING")
                .version(ToolVersion.parse("1.0.0"))
                .build();

        ToolReviewPolicy.ReviewResult result = policy.review(meta);
        assertTrue(result.isApproved());
        assertEquals("自动通过", result.getReason());
    }

    @Test
    void shouldRejectEmptyName() {
        ToolMetadata meta = ToolMetadata.builder()
                .toolId("pub:tool")
                .name("")
                .description("desc")
                .category("DATA_PROCESSING")
                .version(ToolVersion.parse("1.0.0"))
                .build();

        ToolReviewPolicy.ReviewResult result = policy.review(meta);
        assertFalse(result.isApproved());
        assertEquals("工具名称不能为空", result.getReason());
    }

    @Test
    void shouldRejectNullName() {
        ToolMetadata meta = ToolMetadata.builder()
                .toolId("pub:tool")
                .name(null)
                .description("desc")
                .category("DATA_PROCESSING")
                .version(ToolVersion.parse("1.0.0"))
                .build();

        ToolReviewPolicy.ReviewResult result = policy.review(meta);
        assertFalse(result.isApproved());
    }

    @Test
    void shouldRejectEmptyDescription() {
        ToolMetadata meta = ToolMetadata.builder()
                .toolId("pub:tool")
                .name("Tool")
                .description("")
                .category("DATA_PROCESSING")
                .version(ToolVersion.parse("1.0.0"))
                .build();

        ToolReviewPolicy.ReviewResult result = policy.review(meta);
        assertFalse(result.isApproved());
        assertEquals("工具描述不能为空", result.getReason());
    }

    @Test
    void shouldRejectNullCategory() {
        ToolMetadata meta = ToolMetadata.builder()
                .toolId("pub:tool")
                .name("Tool")
                .description("desc")
                .category(null)
                .version(ToolVersion.parse("1.0.0"))
                .build();

        ToolReviewPolicy.ReviewResult result = policy.review(meta);
        assertFalse(result.isApproved());
        assertEquals("工具分类不能为空", result.getReason());
    }

    @Test
    void shouldRejectNullVersion() {
        ToolMetadata meta = ToolMetadata.builder()
                .toolId("pub:tool")
                .name("Tool")
                .description("desc")
                .category("DATA_PROCESSING")
                .version(null)
                .build();

        ToolReviewPolicy.ReviewResult result = policy.review(meta);
        assertFalse(result.isApproved());
        assertEquals("工具版本不能为空", result.getReason());
    }

    @Test
    void shouldRejectEmptyToolId() {
        ToolMetadata meta = ToolMetadata.builder()
                .toolId("")
                .name("Tool")
                .description("desc")
                .category("DATA_PROCESSING")
                .version(ToolVersion.parse("1.0.0"))
                .build();

        ToolReviewPolicy.ReviewResult result = policy.review(meta);
        assertFalse(result.isApproved());
        assertEquals("工具 ID 不能为空", result.getReason());
    }

    @Test
    void shouldRejectNullToolId() {
        ToolMetadata meta = ToolMetadata.builder()
                .toolId(null)
                .name("Tool")
                .description("desc")
                .category("DATA_PROCESSING")
                .version(ToolVersion.parse("1.0.0"))
                .build();

        ToolReviewPolicy.ReviewResult result = policy.review(meta);
        assertFalse(result.isApproved());
    }
}