package com.agentmesh.core.memory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 长期记忆条目
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemoryItem {
    /** 唯一标识 */
    private String id;
    /** 来源会话 */
    private String sessionId;
    /** 记忆内容 */
    private String content;
    /** 类型：PREFERENCE / FACT / CONTEXT */
    private String type;
    /** 元数据 */
    private Map<String, String> metadata;
    /** 创建时间 */
    private LocalDateTime createdAt;
    /** 向量（存储层负责填充） */
    private float[] embedding;
    /** 相似度分数（检索时填充） */
    private double score;
}
