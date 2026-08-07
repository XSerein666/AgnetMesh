package com.agentmesh.core.session;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JDBC 会话存储实现
 * 使用乐观锁 + Append-Only 序号机制保证并发一致性
 * 当前为内存实现，生产环境需替换为 JDBC
 */
@Slf4j
public class JdbcConversationStore implements ConversationStore {

    private final Map<String, List<ChatMessage>> store = new ConcurrentHashMap<>();
    private final Map<String, Integer> sequences = new ConcurrentHashMap<>();

    @Override
    public void append(String sessionId, ChatMessage message) {
        synchronized (getSessionLock(sessionId)) {
            int nextSeq = sequences.getOrDefault(sessionId, 0) + 1;
            sequences.put(sessionId, nextSeq);
            store.computeIfAbsent(sessionId, k -> Collections.synchronizedList(new ArrayList<>()))
                    .add(message);
            log.debug("[JdbcConversationStore] session={}, seq={}, role={}", sessionId, nextSeq, message.getRole());
        }
    }

    @Override
    public List<ChatMessage> getHistory(String sessionId) {
        return store.getOrDefault(sessionId, Collections.emptyList());
    }

    @Override
    public void clear(String sessionId) {
        store.remove(sessionId);
        sequences.remove(sessionId);
    }

    private Object getSessionLock(String sessionId) {
        return sessionId.intern();
    }
}
