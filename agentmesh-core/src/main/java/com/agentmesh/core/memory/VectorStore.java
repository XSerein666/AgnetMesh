package com.agentmesh.core.memory;

import java.util.List;

/**
 * 向量存储接口
 */
public interface VectorStore {

    /** 存储记忆 */
    void store(MemoryItem item);

    /** 语义检索 */
    List<MemoryItem> search(String query, int topK);

    /** 按类型和会话过滤 */
    List<MemoryItem> search(String query, int topK, String sessionId, String type);

    /** 删除记忆 */
    void delete(String id);

    /** 清理指定会话的所有记忆 */
    void clearBySession(String sessionId);
}