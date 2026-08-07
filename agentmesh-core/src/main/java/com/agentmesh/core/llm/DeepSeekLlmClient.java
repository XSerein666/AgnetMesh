package com.agentmesh.core.llm;

import com.agentmesh.core.infrastructure.AgentMeshMetrics;
import com.agentmesh.core.llm.adapter.OpenAiAdapter;

/**
 * DeepSeek LLM 客户端。
 * <p>
 * DeepSeek 完全兼容 OpenAI 协议（/chat/completions 端点 + SSE 流式格式），
 * 因此直接继承 {@link OpenAiLlmClient}，仅在默认 baseUrl 与 provider 标签上做差异化。
 *
 * 注意：DeepSeek 不支持 vision 能力，调用 vision() 会抛出 UnsupportedOperationException。
 */
public class DeepSeekLlmClient extends OpenAiLlmClient {

    private static final String DEFAULT_BASE_URL = "https://api.deepseek.com/v1";
    private static final String DEFAULT_MODEL = "deepseek-chat";

    public DeepSeekLlmClient(String apiKey) {
        this(apiKey, DEFAULT_BASE_URL, DEFAULT_MODEL, null);
    }

    public DeepSeekLlmClient(String apiKey, AgentMeshMetrics metrics) {
        this(apiKey, DEFAULT_BASE_URL, DEFAULT_MODEL, metrics);
    }

    public DeepSeekLlmClient(String apiKey, String baseUrl, String model, AgentMeshMetrics metrics) {
        super(apiKey, baseUrl != null ? baseUrl : DEFAULT_BASE_URL,
                model != null ? model : DEFAULT_MODEL,
                new OpenAiAdapter(), metrics);
    }

    /**
     * 厂商标识用于 metrics 标签，覆盖父类的 "openai"。
     */
    @Override
    protected String getProvider() {
        return "deepseek";
    }

    /**
     * DeepSeek 不支持 vision，显式拒绝。
     */
    @Override
    public String vision(String imageUrl, String prompt) {
        throw new UnsupportedOperationException("DeepSeek 不支持 vision 能力");
    }
}
