package com.jewel.a2a.server.tool;

import com.jewel.a2a.repository.entity.CraftKnowledgeEntity;
import com.agentmesh.core.tool.Tool;
import com.jewel.a2a.server.service.CraftKnowledgeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Tool 4：工艺知识检索（独立 RAG 查询）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SearchCraftKnowledgeTool implements Tool<Map<String, Object>, Object> {

    private final CraftKnowledgeService craftKnowledgeService;

    @Override
    public String getId() {
        return "search_craft_knowledge";
    }

    @Override
    public String getDescription() {
        return "检索珠宝工艺知识库，获取工艺规范和历史案例参考";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "query", Map.of("type", "string", "description", "检索关键词或问题描述")
                ),
                "required", List.of("query")
        );
    }

    @Override
    public Object execute(Map<String, Object> input) {
        String query = (String) input.get("query");
        log.info("[SearchCraftKnowledgeTool] 检索知识, query={}", query);

        try {
            List<CraftKnowledgeEntity> results = craftKnowledgeService.search(query, 0.5, 5);
            List<Map<String, Object>> items = new ArrayList<>();
            for (CraftKnowledgeEntity r : results) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("title", r.getTitle());
                item.put("content", r.getContent());
                item.put("category", r.getCategory());
                item.put("similarity", Math.round(r.getSimilarity() * 10000.0) / 10000.0);
                items.add(item);
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("query", query);
            result.put("count", items.size());
            result.put("results", items);
            return result;

        } catch (Exception e) {
            log.error("[SearchCraftKnowledgeTool] 检索失败", e);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("error", e.getMessage());
            return result;
        }
    }
}