package com.agentmesh.core.routing;

import com.agentmesh.core.llm.LlmClient;
import com.agentmesh.core.protocol.AgentCard;
import com.agentmesh.core.protocol.AgentSkill;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Agent 描述自动生成器。
 * 从 AgentCard 的 skills 信息中，用 LLM 生成中文 description 和 routingTags。
 *
 * 超时控制：Mono.fromCallable().timeout()，与 Phase12 rerank 一致，
 * 超时自动取消上游订阅，不阻塞注册线程。
 */
@Slf4j
public class AgentDescriptionGenerator {

    private static final Pattern JSON_BLOCK = Pattern.compile(
            "\\{[^{}]*\"description\"\\s*:\\s*\"[^\"]*\"\\s*,\\s*\"routingTags\"\\s*:\\s*\\[[^\\]]*\\][^{}]*\\}",
            Pattern.DOTALL);

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;
    private final Duration timeout;

    public AgentDescriptionGenerator(LlmClient llmClient) {
        this(llmClient, Duration.ofSeconds(10));
    }

    public AgentDescriptionGenerator(LlmClient llmClient, Duration timeout) {
        this.llmClient = llmClient;
        this.objectMapper = new ObjectMapper();
        this.timeout = timeout;
    }

    /**
     * 从 AgentCard 生成路由描述。
     * @return {description, routingTags}，生成失败返回 null
     */
    public GeneratedDescription generate(AgentCard card) {
        if (card.getSkills() == null || card.getSkills().isEmpty()) {
            log.debug("[DescGen] Agent {} 无 skills，跳过生成", card.getAgentId());
            return null;
        }

        String prompt = buildPrompt(card);
        try {
            String response = Mono.fromCallable(() -> llmClient.chat(List.of(
                    Map.of("role", "system", "content", getSystemPrompt()),
                    Map.of("role", "user", "content", prompt)
            )))
                    .subscribeOn(Schedulers.boundedElastic())
                    .timeout(timeout)
                    .blockOptional()
                    .orElseThrow(() -> new RuntimeException("LLM 描述生成超时"));
            return parseResponse(response, card.getAgentId());
        } catch (Exception e) {
            log.warn("[DescGen] Agent {} 描述生成失败: {}", card.getAgentId(), e.getMessage());
            return null;
        }
    }

    private String buildPrompt(AgentCard card) {
        StringBuilder sb = new StringBuilder();
        sb.append("Agent 名称：").append(card.getName()).append("\n");
        sb.append("Agent ID：").append(card.getAgentId()).append("\n");
        sb.append("Skills：\n");
        for (AgentSkill skill : card.getSkills()) {
            sb.append("  - ").append(skill.getName());
            if (skill.getDescription() != null) {
                sb.append("：").append(skill.getDescription());
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private String getSystemPrompt() {
        return """
                你是一个 Agent 能力分析器。根据 Agent 的 skills 信息，生成：
                1. 一段简短的中文描述（≤30字），概括该 Agent 能做什么
                2. 3-5个路由标签（中文关键词），用于关键词匹配路由

                严格返回 JSON 格式，不要其他内容：
                {"description":"...","routingTags":["标签1","标签2","标签3"]}
                """;
    }

    private GeneratedDescription parseResponse(String response, String agentId) {
        try {
            Map<String, Object> map = objectMapper.readValue(
                    extractJson(response),
                    new TypeReference<Map<String, Object>>() {});
            Object descObj = map.get("description");
            String description = descObj instanceof String ? (String) descObj : null;
            Object tagsObj = map.get("routingTags");
            @SuppressWarnings("unchecked")
            List<String> tags = tagsObj instanceof List ? (List<String>) tagsObj : null;
            return new GeneratedDescription(description, tags);
        } catch (Exception e) {
            log.warn("[DescGen] Agent {} 响应解析失败: {}", agentId, e.getMessage());
            return null;
        }
    }

    /**
     * 从 LLM 响应中提取 JSON。
     * 策略：先尝试正则匹配目标结构（description + routingTags），
     * 失败则回退到花括号截取。
     */
    String extractJson(String response) {
        String trimmed = response.trim();
        // 去掉 markdown 代码围栏
        if (trimmed.startsWith("```")) {
            int fenceEnd = trimmed.indexOf('\n');
            if (fenceEnd > 0) {
                trimmed = trimmed.substring(fenceEnd + 1);
            }
            if (trimmed.endsWith("```")) {
                trimmed = trimmed.substring(0, trimmed.length() - 3).trim();
            }
        }
        // 正则精确匹配目标 JSON 结构
        Matcher m = JSON_BLOCK.matcher(trimmed);
        if (m.find()) {
            return m.group();
        }
        // 回退：花括号截取
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        return trimmed;
    }

    /** 生成结果 VO */
    public record GeneratedDescription(String description, List<String> routingTags) {}
}
