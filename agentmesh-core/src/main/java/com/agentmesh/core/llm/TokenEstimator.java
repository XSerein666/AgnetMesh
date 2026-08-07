package com.agentmesh.core.llm;

/**
 * Token 估算接口
 * 不同模型可实现各自的 tokenizer 估算逻辑
 */
public interface TokenEstimator {
    int estimateTokens(String text);
}
