package com.agentmesh.core.memory;

import com.agentmesh.core.llm.LlmClient;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 记忆提取器。
 * 用 LLM 从对话中提取值得长期记住的信息（偏好、事实、上下文）。
 */
@Slf4j
public class MemoryExtractor {

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;
    private final int maxItemsPerTurn;

    public MemoryExtractor(LlmClient llmClient, int maxItemsPerTurn) {
        this.llmClient = llmClient;
        this.objectMapper = new ObjectMapper();
        this.maxItemsPerTurn = maxItemsPerTurn;
    }

    /**
     * 从对话历史中提取可记忆信息
     * @param sessionId 会话 ID
     * @param history   对话历史
     * @return 提取的记忆条目列表，失败时返回空列表
     */
    public List<MemoryItem> extract(String sessionId, List<com.agentmesh.core.session.ChatMessage> history) {
        if (history.isEmpty()) {
            return List.of();
        }

        try {
            String conversation = buildConversationText(history);
            String prompt = buildExtractionPrompt(conversation);
            String result = llmClient.chat(List.of(
                    Map.of("role", "user", "content", prompt)
            ));

            List<Map<String, String>> items = objectMapper.readValue(result,
                    new TypeReference<List<Map<String, String>>>() {});

            List<MemoryItem> memoryItems = new ArrayList<>();
            int count = 0;
            for (Map<String, String> item : items) {
                if (count >= maxItemsPerTurn) {
                    break;
                }
                String type = item.getOrDefault("type", "FACT");
                String content = item.get("content");
                if (content == null || content.isEmpty()) {
                    continue;
                }

                memoryItems.add(MemoryItem.builder()
                        .id(UUID.randomUUID().toString())
                        .sessionId(sessionId)
                        .content(content)
                        .type(type)
                        .createdAt(LocalDateTime.now())
                        .build());
                count++;
            }
            log.info("[MemoryExtractor] 提取记忆 {} 条: sessionId={}", memoryItems.size(), sessionId);
            return memoryItems;
        } catch (Exception e) {
            log.warn("[MemoryExtractor] 记忆提取失败: sessionId={}, error={}", sessionId, e.getMessage());
            return List.of();
        }
    }

    private String buildConversationText(List<com.agentmesh.core.session.ChatMessage> history) {
        StringBuilder sb = new StringBuilder();
        for (com.agentmesh.core.session.ChatMessage msg : history) {
            if (!"system".equals(msg.getRole())) {
                sb.append(msg.getRole()).append(": ").append(msg.getContent()).append("\n");
            }
        }
        return sb.toString();
    }

    private String buildExtractionPrompt(String conversation) {
        return """
                从以下对话中提取值得长期记住的信息，以 JSON 数组格式返回。
                
                提取类型：
                1. PREFERENCE：用户明确表达的风格、习惯、喜好
                2. FACT：对话中提到的客观事实
                3. CONTEXT：任务背景、决策依据
                
                对话：
                """ + conversation + """
                
                返回格式示例：
                [{"type": "PREFERENCE", "content": "用户喜欢简约风格"}]
                
                只返回 JSON 数组，不要其他内容。""";
    }
}
