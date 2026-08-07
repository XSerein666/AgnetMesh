package com.agentmesh.core.session;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存会话存储实现
 */
@Slf4j
public class InMemoryConversationStore implements ConversationStore {

    private final ConcurrentHashMap<String, List<ChatMessage>> store = new ConcurrentHashMap<>();
    private final int maxHistory;

    public InMemoryConversationStore() {
        this(50);
    }

    public InMemoryConversationStore(int maxHistory) {
        this.maxHistory = maxHistory;
    }

    @Override
    public void append(String sessionId, ChatMessage message) {
        store.compute(sessionId, (k, v) -> {
            if (v == null) {
                v = new ArrayList<>();
            }
            v.add(message);
            // 超过最大历史，移除最早的
            while (v.size() > maxHistory) {
                v.remove(0);
            }
            return v;
        });
    }

    @Override
    public List<ChatMessage> getHistory(String sessionId) {
        List<ChatMessage> history = store.get(sessionId);
        return history != null ? Collections.unmodifiableList(history) : Collections.emptyList();
    }

    @Override
    public void clear(String sessionId) {
        store.remove(sessionId);
    }
}
