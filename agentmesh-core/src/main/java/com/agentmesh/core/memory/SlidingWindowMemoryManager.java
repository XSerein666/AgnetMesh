package com.agentmesh.core.memory;

import com.agentmesh.core.llm.LlmClient;
import com.agentmesh.core.llm.TokenEstimator;
import com.agentmesh.core.session.ChatMessage;
import com.agentmesh.core.session.ConversationStore;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 滑动窗口 + 摘要压缩的记忆管理实现。
 * <p>
 * 策略：
 * - 消息数未超 summaryThreshold → 返回全部消息
 * - 消息数超出 summaryThreshold → 早期消息压缩为摘要，窗口内消息保留
 * - 摘要由 LLM 生成，失败时降级为简单截断
 */
@Slf4j
public class SlidingWindowMemoryManager implements MemoryManager {

    private final ConversationStore conversationStore;
    private final LlmClient summaryLlmClient;
    private final MemoryProperties properties;
    private final TokenEstimator tokenEstimator;
    private final VectorStore vectorStore;
    private final MemoryExtractor memoryExtractor;

    /** 会话摘要缓存 */
    private final ConcurrentHashMap<String, String> summaries = new ConcurrentHashMap<>();

    public SlidingWindowMemoryManager(ConversationStore conversationStore,
                                       LlmClient summaryLlmClient,
                                       MemoryProperties properties) {
        this(conversationStore, summaryLlmClient, properties, null, null);
    }

    public SlidingWindowMemoryManager(ConversationStore conversationStore,
                                       LlmClient summaryLlmClient,
                                       MemoryProperties properties,
                                       VectorStore vectorStore,
                                       MemoryExtractor memoryExtractor) {
        this.conversationStore = conversationStore;
        this.summaryLlmClient = summaryLlmClient;
        this.properties = properties;
        this.tokenEstimator = summaryLlmClient.getTokenEstimator();
        this.vectorStore = vectorStore;
        this.memoryExtractor = memoryExtractor;
    }

    @Override
    public void append(String sessionId, ChatMessage message) {
        conversationStore.append(sessionId, message);
        List<ChatMessage> history = conversationStore.getHistory(sessionId);
        if (history.size() >= properties.getShortTerm().getSummaryThreshold()) {
            triggerSummary(sessionId, history);
        }
    }

    @Override
    public List<ChatMessage> recall(String sessionId, int maxTokens) {
        List<ChatMessage> history = conversationStore.getHistory(sessionId);
        if (history.isEmpty()) {
            return List.of();
        }

        int windowSize = properties.getShortTerm().getWindowSize();
        if (history.size() <= windowSize) {
            return fitTokens(history, maxTokens);
        }

        // 分割：窗口内消息 + 窗口外消息
        int splitIndex = Math.max(0, history.size() - windowSize);
        List<ChatMessage> windowMsgs = new ArrayList<>(history.subList(splitIndex, history.size()));

        // 生成或获取摘要
        String summary = summaries.get(sessionId);
        if (summary == null) {
            summary = generateSummary(sessionId, history);
        }

        // 组装：摘要 + 窗口消息
        List<ChatMessage> result = new ArrayList<>();
        if (summary != null && !summary.isEmpty()) {
            result.add(ChatMessage.builder()
                    .role("system")
                    .content("[对话摘要] " + summary)
                    .build());
        }
        result.addAll(windowMsgs);

        return fitTokens(result, maxTokens);
    }

    @Override
    public void compress(String sessionId) {
        List<ChatMessage> history = conversationStore.getHistory(sessionId);
        if (history.isEmpty()) return;
        triggerSummary(sessionId, history);
    }

    @Override
    public String getSummary(String sessionId) {
        return summaries.getOrDefault(sessionId, "");
    }

    @Override
    public String getLongTermMemory(String sessionId, String query) {
        if (vectorStore == null || query == null || query.isEmpty()) {
            return "";
        }
        try {
            List<MemoryItem> items = vectorStore.search(query,
                    properties.getLongTerm().getRetrieval().getTopK());
            if (items.isEmpty()) return "";
            StringBuilder sb = new StringBuilder("\n[关于用户的长期记忆]\n");
            for (MemoryItem item : items) {
                sb.append("- ").append(item.getContent()).append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            log.warn("[MemoryManager] 长期记忆检索失败: {}", e.getMessage());
            return "";
        }
    }

    /**
     * 获取记忆提取器（供外部在对话结束后调用）
     */
    public MemoryExtractor getMemoryExtractor() {
        return memoryExtractor;
    }

    /**
     * 获取向量存储（供外部使用）
     */
    public VectorStore getVectorStore() {
        return vectorStore;
    }

    // ========== 内部方法 ==========

    private void triggerSummary(String sessionId, List<ChatMessage> history) {
        String summary = generateSummary(sessionId, history);
        if (summary != null) {
            summaries.put(sessionId, summary);
        }
    }

    private String generateSummary(String sessionId, List<ChatMessage> history) {
        try {
            String conversation = buildConversationText(history);
            String prompt = buildSummaryPrompt(conversation);
            String result = summaryLlmClient.chat(List.of(
                    Map.of("role", "user", "content", prompt)
            ));
            log.info("[MemoryManager] 摘要生成成功: sessionId={}, length={}", sessionId, result.length());
            return result;
        } catch (Exception e) {
            log.warn("[MemoryManager] 摘要生成失败，降级为简单截断: sessionId={}, error={}",
                    sessionId, e.getMessage());
            return "[摘要生成失败，对话已截断]";
        }
    }

    private String buildConversationText(List<ChatMessage> history) {
        StringBuilder sb = new StringBuilder();
        int windowSize = properties.getShortTerm().getWindowSize();
        int endIndex = Math.max(0, history.size() - windowSize);
        for (int i = 0; i < endIndex; i++) {
            ChatMessage msg = history.get(i);
            if (!"system".equals(msg.getRole())) {
                sb.append(msg.getRole()).append(": ").append(msg.getContent()).append("\n");
            }
        }
        return sb.toString();
    }

    private String buildSummaryPrompt(String conversation) {
        return "请用一段话（不超过 " + properties.getShortTerm().getMaxSummaryTokens()
                + " tokens）总结以下对话的关键信息、用户偏好和重要决策：\n\n" + conversation;
    }

    /**
     * 按 Token 数截断消息列表，优先保留后面的消息
     */
    private List<ChatMessage> fitTokens(List<ChatMessage> messages, int maxTokens) {
        if (messages.isEmpty()) return messages;

        int totalTokens = messages.stream()
                .mapToInt(m -> tokenEstimator.estimateTokens(m.getContent() != null ? m.getContent() : ""))
                .sum();
        if (totalTokens <= maxTokens) {
            return messages;
        }

        List<ChatMessage> result = new ArrayList<>();
        int currentTokens = 0;
        // 从后往前保留，优先保留最近的消息
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage msg = messages.get(i);
            int msgTokens = tokenEstimator.estimateTokens(
                    msg.getContent() != null ? msg.getContent() : "");
            if (currentTokens + msgTokens > maxTokens) {
                break;
            }
            result.add(0, msg);
            currentTokens += msgTokens;
        }
        return result;
    }
}