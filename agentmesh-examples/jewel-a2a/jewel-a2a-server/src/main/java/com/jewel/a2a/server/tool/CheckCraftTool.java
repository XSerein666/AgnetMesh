package com.jewel.a2a.server.tool;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jewel.a2a.repository.entity.CraftKnowledgeEntity;
import com.agentmesh.core.llm.LlmClient;
import com.agentmesh.core.tool.Tool;
import com.jewel.a2a.server.service.CraftKnowledgeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tool 3：工艺校验（使用 AgentMesh LlmClient.vision() + RAG 知识库增强）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CheckCraftTool implements Tool<Map<String, Object>, Object> {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CraftKnowledgeService craftKnowledgeService;
    private final LlmClient llmClient;

    @Override
    public String getId() {
        return "check_craft_feasibility";
    }

    @Override
    public String getDescription() {
        return "校验设计方案的物理与工艺可行性，指出缺陷并给出建议";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "imageUrl", Map.of("type", "string", "description", "待校验的设计图URL")
                ),
                "required", List.of("imageUrl")
        );
    }

    @Override
    public Object execute(Map<String, Object> input) {
        String imageUrl = (String) input.get("imageUrl");
        log.info("[CheckCraftTool] 开始分析, imageUrl={}", imageUrl);

        try {
            // RAG 检索相关知识
            String userQuestion = "请分析这张珠宝设计图的工艺可行性";
            List<CraftKnowledgeEntity> knowledgeList = craftKnowledgeService.search(userQuestion, 0.5, 5);
            String context = craftKnowledgeService.buildContext(knowledgeList);
            log.info("[CheckCraftTool] 检索到 {} 条相关知识，已注入上下文", knowledgeList.size());

            // 使用 AgentMesh 统一 Vision 接口
            String systemPrompt = context.isEmpty()
                    ? CraftCheckPrompt.SYSTEM_PROMPT
                    : CraftCheckPrompt.SYSTEM_PROMPT + context;
            String fullPrompt = systemPrompt + "\n\n请分析这张珠宝设计图的工艺可行性";
            String aiResponse = llmClient.vision(imageUrl, fullPrompt);

            Map<String, Object> report = parseReport(aiResponse);
            log.info("[CheckCraftTool] 分析完成, pass={}, score={}",
                    report.get("pass"), report.get("score"));
            return report;
        } catch (Exception e) {
            log.error("[CheckCraftTool] 分析失败", e);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("error", e.getMessage());
            return result;
        }
    }

    private Map<String, Object> parseReport(String aiText) throws Exception {
        // 清洗可能的 markdown 标记
        String json = aiText.trim();
        if (json.startsWith("```json")) {
            json = json.substring(7);
        } else if (json.startsWith("```")) {
            json = json.substring(3);
        }
        if (json.endsWith("```")) {
            json = json.substring(0, json.length() - 3);
        }
        json = json.trim();

        return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
    }
}