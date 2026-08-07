package com.jewel.a2a.server.tool;

import com.agentmesh.core.tool.Tool;
import com.agentmesh.core.tool.ToolRegistry;
import com.agentmesh.core.tool.marketplace.model.ToolMetadata;
import com.agentmesh.core.tool.marketplace.model.ToolVersion;
import com.agentmesh.core.tool.marketplace.service.ToolMarketplace;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 启动时将珠宝工具发布到 Tool Marketplace。
 * 仅在 ToolMarketplace Bean 可用时（marketplace 启用时）执行。
 */
@Slf4j
@Component
@ConditionalOnBean(ToolMarketplace.class)
public class ToolPublisher implements CommandLineRunner {

    private final ToolMarketplace marketplace;
    private final ToolRegistry toolRegistry;

    public ToolPublisher(ToolMarketplace marketplace,
                         @Autowired(required = false) ToolRegistry toolRegistry) {
        this.marketplace = marketplace;
        this.toolRegistry = toolRegistry;
    }

    @Override
    public void run(String... args) {
        if (toolRegistry == null) {
            log.warn("[ToolPublisher] ToolRegistry 不可用，跳过发布");
            return;
        }
        log.info("[ToolPublisher] 开始发布工具到 Marketplace...");

        publishTool("analyze_jewelry_image", "JEWELRY_IMAGE",
                "分析珠宝图片，提取设计参数（主石、材质、工艺等）",
                List.of("珠宝", "图片分析", "鉴定"), "1.0.0");

        publishTool("generate_jewelry_design", "JEWELRY_DESIGN",
                "根据设计参数生成专业级珠宝设计图",
                List.of("珠宝", "设计", "生成"), "1.0.0");

        publishTool("check_craft_feasibility", "JEWELRY_CRAFT",
                "校验设计方案的物理与工艺可行性",
                List.of("珠宝", "工艺", "校验"), "1.0.0");

        publishTool("search_craft_knowledge", "JEWELRY_CRAFT",
                "检索珠宝工艺知识库，获取工艺规范和历史案例",
                List.of("珠宝", "知识", "检索"), "1.0.0");

        log.info("[ToolPublisher] 工具发布完成，共 4 个工具");
    }

    private void publishTool(String toolId, String category, String description,
                              List<String> tags, String version) {
        try {
            Tool<?, ?> tool = toolRegistry.getTool(toolId);
            if (tool == null) {
                log.warn("[ToolPublisher] 工具未找到: {}", toolId);
                return;
            }
            ToolMetadata metadata = ToolMetadata.builder()
                    .name(toolId)
                    .category(category)
                    .description(description)
                    .tags(tags)
                    .version(ToolVersion.parse(version))
                    .build();
            marketplace.submit(tool, metadata);
            log.info("[ToolPublisher] 已发布: {}", toolId);
        } catch (Exception e) {
            log.warn("[ToolPublisher] 发布失败: {}, reason={}", toolId, e.getMessage());
        }
    }
}