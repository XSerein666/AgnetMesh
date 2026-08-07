package com.jewel.a2a.server.tool;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.agentmesh.core.llm.LlmClient;
import com.agentmesh.core.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tool 1：珠宝图片分析（使用 AgentMesh LlmClient.vision()）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnalyzeImageTool implements Tool<Map<String, Object>, Object> {

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

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final LlmClient llmClient;

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
            // 使用 AgentMesh 统一 Vision 接口
            String aiResponse = llmClient.vision(imageUrl, ANALYSIS_PROMPT);

            // 清洗 markdown 标记
            String text = aiResponse.trim();
            if (text.startsWith("```json")) {
                text = text.substring(7);
            } else if (text.startsWith("```")) {
                text = text.substring(3);
            }
            if (text.endsWith("```")) {
                text = text.substring(0, text.length() - 3);
            }

            Map<String, Object> result = objectMapper.readValue(text.trim(),
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
}
