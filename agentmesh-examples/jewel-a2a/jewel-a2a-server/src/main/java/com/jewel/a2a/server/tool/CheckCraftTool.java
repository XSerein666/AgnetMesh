package com.jewel.a2a.server.tool;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jewel.a2a.repository.entity.CraftKnowledgeEntity;
import com.agentmesh.core.tool.Tool;
import com.jewel.a2a.server.service.CraftKnowledgeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tool 3：工艺校验（调用 DashScope qwen-vl-plus + RAG 知识库增强）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CheckCraftTool implements Tool<Map<String, Object>, Object> {

    private static final String API_URL =
            "https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation";
    private static final String MODEL = "qwen-vl-plus";

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CraftKnowledgeService craftKnowledgeService;

    @Value("${dashscope.api-key}")
    private String apiKey;

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

            String aiResponse = callVisionAPI(imageUrl, context);
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

    @SuppressWarnings("unchecked")
    private String callVisionAPI(String imageUrl, String context) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + apiKey);

        // 构建 system message（含 RAG 检索上下文）
        String systemPrompt = context.isEmpty()
                ? CraftCheckPrompt.SYSTEM_PROMPT
                : CraftCheckPrompt.SYSTEM_PROMPT + context;
        Map<String, Object> systemMsg = new LinkedHashMap<>();
        systemMsg.put("role", "system");
        systemMsg.put("content", List.of(
                Map.of("text", systemPrompt)
        ));

        // 构建 user message（图片 + 文字）
        Map<String, Object> userMsg = new LinkedHashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", List.of(
                Map.of("image", imageUrl),
                Map.of("text", "请分析这张珠宝设计图的工艺可行性")
        ));

        // 构建请求体
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", MODEL);
        Map<String, Object> inputParam = new LinkedHashMap<>();
        inputParam.put("messages", List.of(systemMsg, userMsg));
        body.put("input", inputParam);

        HttpEntity<String> request = new HttpEntity<>(objectMapper.writeValueAsString(body), headers);
        ResponseEntity<String> response = restTemplate.postForEntity(API_URL, request, String.class);

        // 解析响应，提取 choices[0].message.content[0].text
        Map<String, Object> respBody = objectMapper.readValue(response.getBody(), Map.class);
        Map<String, Object> output = (Map<String, Object>) respBody.get("output");
        List<Map<String, Object>> choices = (List<Map<String, Object>>) output.get("choices");
        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
        List<Map<String, Object>> content = (List<Map<String, Object>>) message.get("content");
        return (String) content.get(0).get("text");
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