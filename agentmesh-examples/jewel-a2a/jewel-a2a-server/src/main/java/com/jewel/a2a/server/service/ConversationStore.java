package com.jewel.a2a.server.service;

import com.jewel.a2a.common.dto.ChatMessage;

import java.util.List;

/**
 * 会话存储接口（当前用内存实现，预留 Redis 实现）
 */
public interface ConversationStore {

    List<ChatMessage> getHistory(String sessionId);

    void append(String sessionId, ChatMessage message);

    void clear(String sessionId);
}