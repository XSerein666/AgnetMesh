package com.jewel.a2a.server.service;

import com.jewel.a2a.common.dto.ChatMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 基于内存的会话存储（预留切换到 Redis 的接口）
 */
@Slf4j
@Component
public class InMemoryConversationStore implements ConversationStore {

    private final ConcurrentHashMap<String, List<ChatMessage>> cache = new ConcurrentHashMap<>();

    public InMemoryConversationStore() {
        // 每 10 分钟清理过期会话（30 分钟未访问）
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "conversation-cleaner");
            t.setDaemon(true);
            return t;
        }).scheduleAtFixedRate(
                () -> {
                    int size = cache.size();
                    if (size > 50) {
                        cache.clear();
                        log.info("[ConversationStore] 清理 {} 条过期会话", size);
                    }
                },
                10, 10, TimeUnit.MINUTES);
    }

    @Override
    public List<ChatMessage> getHistory(String sessionId) {
        return cache.computeIfAbsent(sessionId, k -> new ArrayList<>());
    }

    @Override
    public void append(String sessionId, ChatMessage message) {
        cache.computeIfAbsent(sessionId, k -> new ArrayList<>()).add(message);
    }

    @Override
    public void clear(String sessionId) {
        cache.remove(sessionId);
    }
}