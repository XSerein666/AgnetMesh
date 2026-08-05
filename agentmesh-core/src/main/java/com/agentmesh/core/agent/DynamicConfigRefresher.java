package com.agentmesh.core.agent;

import com.agentmesh.core.llm.config.LlmProperties;

import java.util.function.Consumer;

/**
 * 动态配置刷新接口（预留，后续集成 Nacos/Apollo）
 */
public interface DynamicConfigRefresher {

    /** 刷新 LLM 配置（API Key、模型切换等） */
    void refreshLlmConfig(LlmProperties newProps);

    /** 刷新 Prompt 模板 */
    void refreshPromptTemplate(String templateName);

    /** 监听配置变更 */
    void registerListener(String configKey, Consumer<Object> callback);
}