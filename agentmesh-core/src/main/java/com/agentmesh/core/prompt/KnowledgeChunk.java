package com.agentmesh.core.prompt;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 知识块（用于 RAG 检索结果的 Token 预算管理）
 */
@Data
@AllArgsConstructor
public class KnowledgeChunk {
    private String title;
    private String content;
    private double score;
}