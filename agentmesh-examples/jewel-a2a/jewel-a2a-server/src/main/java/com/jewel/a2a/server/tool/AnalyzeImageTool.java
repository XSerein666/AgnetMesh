package com.jewel.a2a.server.tool;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.agentmesh.core.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tool 1：珠宝图片分析（调用 DashScope qwen-vl-plus）
 */
@Slf4j
@Component
public class AnalyzeImageTool implements Tool<Map<String, Object>, Object> {

    private static final String API_URL =
            "https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation";
    private static final String MODEL = "qwen-vl-plus";

    private static final String ANALYSIS_PROMPT = """
            你是一位资深珠宝鉴定师和设计师。请分析这张珠宝图片，从以下维度给出详细报告：

            1. 主石类型（钻石、红宝石、蓝宝石、祖母绿等）
            2. 主石克拉数估算
            3. 金属材质（铂金、18K白金、18K黄金、18K玫瑰金等）
            4. 镶嵌工艺（爪镶、包镶、轨道镶、密钉镶等）
            5. 设计风格（经典款、复古款、现代简约、奢华款等）
            6. 整体设计评价（50字以内）

            请严格返回JSON格式，不要包含其他文字：
            {
              "stoneType": "主石类型",
              "carat": 克拉数估算值,
              "metal": "金属材质",
              "craft": "镶嵌工艺",
              "style": "设计风格",
              "evaluation": "整体评价"
            }""";

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${dashscope.api-key}")
    private String apiKey;

    @Override
    public String getId() {
        return "analyze_jewelry_image";
    }

    @Override
    public String getDescription() {
        return "分析珠宝图片，提取设计参数（主石、材质、工艺等）";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "imageUrl", Map.of("type", "string", "description", "图片URL或Base64")
                ),
                "required", List.of("imageUrl")
        );
    }

    @Override
    public Object execute(Map<String, Object> input) {
        String imageUrl = (String) input.get("imageUrl");
        log.info("[AnalyzeImageTool] 开始分析, imageUrl={}", imageUrl);

        try {
            String aiResponse = callVisionAPI(imageUrl);
            Map<String, Object> result = objectMapper.readValue(aiResponse,
                    new TypeReference<Map<String, Object>>() {});
            log.info("[AnalyzeImageTool] 分析完成, stoneType={}, style={}",
                    result.get("stoneType"), result.get("style"));
            return result;
        } catch (Exception e) {
            log.error("[AnalyzeImageTool] 分析失败", e);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("error", e.getMessage());
            return result;
        }
    }

    @SuppressWarnings("unchecked")
    private String callVisionAPI(String imageUrl) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + apiKey);

        // system message
        Map<String, Object> systemMsg = new LinkedHashMap<>();
        systemMsg.put("role", "system");
        systemMsg.put("content", List.of(
                Map.of("text", "你是一位珠宝鉴定师，请用JSON格式回答。")
        ));

        // user message（图片 + 文字）
        Map<String, Object> userMsg = new LinkedHashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", List.of(
                Map.of("image", imageUrl),
                Map.of("text", ANALYSIS_PROMPT)
        ));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", MODEL);
        Map<String, Object> inputParam = new LinkedHashMap<>();
        inputParam.put("messages", List.of(systemMsg, userMsg));
        body.put("input", inputParam);

        HttpEntity<String> request = new HttpEntity<>(objectMapper.writeValueAsString(body), headers);
        ResponseEntity<String> response = restTemplate.postForEntity(API_URL, request, String.class);

        Map<String, Object> respBody = objectMapper.readValue(response.getBody(), Map.class);
        Map<String, Object> output = (Map<String, Object>) respBody.get("output");
        List<Map<String, Object>> choices = (List<Map<String, Object>>) output.get("choices");
        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
        List<Map<String, Object>> content = (List<Map<String, Object>>) message.get("content");
        String text = (String) content.get(0).get("text");

        // 清洗 markdown 标记
        text = text.trim();
        if (text.startsWith("```json")) {
            text = text.substring(7);
        } else if (text.startsWith("```")) {
            text = text.substring(3);
        }
        if (text.endsWith("```")) {
            text = text.substring(0, text.length() - 3);
        }
        return text.trim();
    }
}