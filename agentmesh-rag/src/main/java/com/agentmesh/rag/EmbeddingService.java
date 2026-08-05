package com.agentmesh.rag;

import java.util.List;

/**
 * 向量化服务接口
 */
public interface EmbeddingService {

    /**
     * 将文本转换为向量
     * @param text 输入文本
     * @return 向量（1024维）
     */
    List<Double> embed(String text);
}