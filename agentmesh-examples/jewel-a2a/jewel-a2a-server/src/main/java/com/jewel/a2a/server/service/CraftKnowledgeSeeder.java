package com.jewel.a2a.server.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jewel.a2a.repository.mapper.CraftKnowledgeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * 工艺知识库种子数据初始化（从 knowledge_seed.json 读取）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CraftKnowledgeSeeder implements CommandLineRunner {

    private static final String SEED_FILE = "knowledge_seed.json";

    private final CraftKnowledgeMapper mapper;
    private final CraftKnowledgeService service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void run(String... args) {
        log.info("[CraftKnowledgeSeeder] 检查种子数据...");

        try {
            ClassPathResource resource = new ClassPathResource(SEED_FILE);
            List<Map<String, Object>> items;
            try (InputStream is = resource.getInputStream()) {
                items = objectMapper.readValue(is, new TypeReference<>() {});
            }

            int added = 0;
            int skipped = 0;
            for (Map<String, Object> item : items) {
                String title = (String) item.get("title");
                String content = (String) item.get("content");
                String category = (String) item.get("category");
                Object metadata = item.getOrDefault("metadata", Map.of());

                if (mapper.countByTitle(title) > 0) {
                    skipped++;
                    continue;
                }

                try {
                    service.insert(title, content, category, metadata);
                    added++;
                } catch (Exception e) {
                    log.warn("[CraftKnowledgeSeeder] 种子数据插入失败: {} - {}", title, e.getMessage());
                }
            }

            log.info("[CraftKnowledgeSeeder] 种子数据检查完成，新增 {} 条，跳过 {} 条", added, skipped);
        } catch (Exception e) {
            log.error("[CraftKnowledgeSeeder] 种子数据文件读取失败: {}", e.getMessage(), e);
        }
    }
}