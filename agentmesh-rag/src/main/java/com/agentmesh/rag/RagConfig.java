package com.agentmesh.rag;

import lombok.Builder;
import lombok.Data;

/**
 * RAG 配置类
 * 所有配置由调用方通过构造函数注入，不硬编码，保证 rag 模块的独立性
 */
@Data
@Builder
public class RagConfig {
    /** 向量库类型：pgvector | milvus | elasticsearch */
    @Builder.Default
    private String vectorStoreType = "pgvector";

    /** 向量库连接地址 */
    private String vectorStoreUrl;

    /** Embedding 服务 API Key */
    private String embeddingApiKey;

    /** Embedding 模型名 */
    @Builder.Default
    private String embeddingModel = "text-embedding-v2";

    /** 索引/集合名称 */
    @Builder.Default
    private String indexName = "AgentMesh_knowledge";

    /** 检索返回数量 */
    @Builder.Default
    private int topK = 5;

    /** 相似度阈值（低于此值的结果丢弃） */
    @Builder.Default
    private double similarityThreshold = 0.7;

    /** Embedding 维度 */
    @Builder.Default
    private int embeddingDimension = 1536;
}