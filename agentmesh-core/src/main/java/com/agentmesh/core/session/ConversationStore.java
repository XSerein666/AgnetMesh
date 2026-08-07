package com.agentmesh.core.session;

import java.util.List;

/**
 * 会话存储接口
 */
public interface ConversationStore {

    /** 追加消息 */
    void append(String sessionId, ChatMessage message);

    /** 获取历史消息 */
    List<ChatMessage> getHistory(String sessionId);

    /** 清除会话 */
    void clear(String sessionId);
}
