package com.agentmesh.core.memory;

import com.agentmesh.core.session.ChatMessage;

import java.util.List;

/**
 * 记忆管理接口。
 * 作为 ConversationStore 和 ReActAgent 之间的中间层，负责：
 * - 滑动窗口：保留最近 N 轮消息
 * - 摘要压缩：早期消息压缩为摘要
 * - 长期记忆注入：检索相关记忆注入 system prompt
 */
public interface MemoryManager {

    /**
     * 追加消息，自动触发记忆管理判断
     */
    void append(String sessionId, ChatMessage message);

    /**
     * 召回压缩后的消息列表，保证不超过 maxTokens
     * @return 压缩后的消息列表（含摘要消息 + 窗口内原始消息）
     */
    List<ChatMessage> recall(String sessionId, int maxTokens);

    /**
     * 强制压缩摘要
     */
    void compress(String sessionId);

    /**
     * 获取当前摘要文本
     */
    String getSummary(String sessionId);

    /**
     * 获取长期记忆文本（注入 system prompt），未启用长期记忆时返回空字符串
     */
    String getLongTermMemory(String sessionId, String query);
}