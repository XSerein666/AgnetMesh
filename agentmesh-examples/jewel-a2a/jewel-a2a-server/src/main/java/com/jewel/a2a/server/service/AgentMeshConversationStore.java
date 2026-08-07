package com.jewel.a2a.server.service;

import com.agentmesh.core.session.ChatMessage;
import com.agentmesh.core.session.ConversationStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jewel.a2a.repository.entity.ConversationEntity;
import com.jewel.a2a.repository.mapper.ConversationMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AgentMesh ConversationStore 适配器。
 * <p>
 * 实现 AgentMesh 的 ConversationStore 接口，底层使用内存缓存 + MyBatis-Plus 持久化到 PostgreSQL。
 * 内存缓存优先，数据库作为持久化存储。
 */
@Slf4j
@Component("agentMeshConversationStore")
public class AgentMeshConversationStore implements ConversationStore {

    private final ConcurrentHashMap<String, List<ChatMessage>> cache = new ConcurrentHashMap<>();
    private final ConversationMapper conversationMapper;
    private final ObjectMapper objectMapper;

    public AgentMeshConversationStore(ConversationMapper conversationMapper) {
        this.conversationMapper = conversationMapper;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public void append(String sessionId, ChatMessage message) {
        cache.computeIfAbsent(sessionId, k -> new ArrayList<>()).add(message);
    }

    @Override
    public List<ChatMessage> getHistory(String sessionId) {
        List<ChatMessage> cached = cache.get(sessionId);
        if (cached != null && !cached.isEmpty()) {
            return new ArrayList<>(cached);
        }
        // 从数据库恢复
        try {
            ConversationEntity entity = conversationMapper.findBySessionId(sessionId);
            if (entity != null && entity.getMessages() != null) {
                ChatMessage[] msgs = objectMapper.convertValue(entity.getMessages(), ChatMessage[].class);
                List<ChatMessage> list = new ArrayList<>(Arrays.asList(msgs));
                cache.put(sessionId, list);
                return list;
            }
        } catch (Exception e) {
            log.warn("[ConversationStore] 会话历史恢复失败: sessionId={}", sessionId, e);
        }
        return new ArrayList<>();
    }

    @Override
    public void clear(String sessionId) {
        cache.remove(sessionId);
    }

    /**
     * 持久化到数据库
     */
    public void persist(String sessionId) {
        List<ChatMessage> history = cache.get(sessionId);
        if (history == null || history.isEmpty()) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(history);
            ConversationEntity exist = conversationMapper.findBySessionId(sessionId);
            if (exist != null) {
                exist.setMessages(json);
                exist.setUpdatedAt(LocalDateTime.now());
                conversationMapper.updateById(exist);
            } else {
                ConversationEntity entity = new ConversationEntity();
                entity.setSessionId(sessionId);
                entity.setMessages(json);
                entity.setCreatedAt(LocalDateTime.now());
                entity.setUpdatedAt(LocalDateTime.now());
                conversationMapper.insert(entity);
            }
        } catch (Exception e) {
            log.warn("[ConversationStore] 会话持久化失败: sessionId={}", sessionId, e);
        }
    }
}
