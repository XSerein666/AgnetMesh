package com.agentmesh.core.llm.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * LLM 配置属性
 */
@Data
@ConfigurationProperties(prefix = "agentmesh.llm")
public class LlmProperties {
    /** 提供商：dashscope | openai | ollama */
    private String provider = "dashscope";

    /** 百炼配置 */
    private DashScope dashscope = new DashScope();

    /** OpenAI 配置 */
    private OpenAi openai = new OpenAi();

    /** Ollama 配置 */
    private Ollama ollama = new Ollama();

    /** 超时配置 */
    private Timeout timeout = new Timeout();

    /** 工具执行器配置 */
    private ToolExecutorConfig toolExecutor = new ToolExecutorConfig();

    @Data
    public static class DashScope {
        private String apiKey;
        private String textModel = "qwen-plus";
        private String visionModel = "qwen-vl-plus";
    }

    @Data
    public static class OpenAi {
        private String apiKey;
        private String baseUrl = "https://api.openai.com/v1";
        private String model = "gpt-4o";
    }

    @Data
    public static class Ollama {
        private String baseUrl = "http://localhost:11434";
        private String model = "qwen2.5";
    }

    @Data
    public static class Timeout {
        /** 连接超时（秒） */
        private int connect = 10;
        /** 读取超时（秒） */
        private int read = 60;
        /** 总请求超时（秒） */
        private int request = 120;
    }

    @Data
    public static class ToolExecutorConfig {
        private int corePoolSize = 2;
        private int maxPoolSize = 10;
        private int keepAliveSeconds = 60;
        private int executionTimeoutSeconds = 30;
    }
}