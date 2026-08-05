package com.agentmesh.demo.tool;

import com.agentmesh.core.tool.Tool;
import com.agentmesh.rag.KnowledgeEntity;
import com.agentmesh.rag.KnowledgeService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 知识检索 Tool
 * 让 LLM 可以自主检索知识库，像调用 weather 一样调用 knowledge
 */
@Component
public class KnowledgeTool implements Tool<Map<String, Object>, Map<String, Object>> {

    private final KnowledgeService knowledgeService;

    public KnowledgeTool(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    @Override
    public String getId() {
        return "knowledge";
    }

    @Override
    public String getDescription() {
        return "检索知识库中的相关文档。当你需要查找特定信息、技术文档、"
             + "或不确定的知识时，使用此工具。输入为自然语言查询。";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "query", Map.of(
                                "type", "string",
                                "description", "检索查询，用自然语言描述你想查找的内容"
                        )
                ),
                "required", List.of("query")
        );
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> input) {
        String query = (String) input.get("query");
        if (query == null || query.isBlank()) {
            return Map.of("error", "查询参数不能为空");
        }

        List<KnowledgeEntity> results = knowledgeService.search(query);
        if (results.isEmpty()) {
            return Map.of("results", List.of(),
                    "message", "未找到相关文档");
        }

        List<Map<String, Object>> formattedResults = results.stream()
                .map(r -> Map.<String, Object>of(
                        "title", r.getTitle() != null ? r.getTitle() : "",
                        "content", r.getContent(),
                        "category", r.getCategory() != null ? r.getCategory() : "未知",
                        "score", r.getSimilarity() != null ? r.getSimilarity() : 0.0
                ))
                .collect(Collectors.toList());

        return Map.of(
                "results", formattedResults,
                "count", formattedResults.size()
        );
    }
}