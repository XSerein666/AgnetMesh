package com.agentmesh.core.routing;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 路由配置属性。
 */
@Data
@ConfigurationProperties(prefix = "agentmesh.routing")
public class RoutingProperties {
    /** 路由策略：keyword | llm | ab */
    private String strategy = "keyword";

    private Llm llm = new Llm();
    private Ab ab = new Ab();

    @Data
    public static class Llm {
        /** 阶段1粗筛 Top-K */
        private int topK = 5;
        /** 跳过粗筛的注册数阈值 */
        private int skipRecallThreshold = 10;
        /** 低置信度回退阈值 */
        private double confidenceThreshold = 0.6;
        /** LLM 调用超时（秒） */
        private int timeout = 5;
        /** 缓存配置 */
        private Cache cache = new Cache();
    }

    @Data
    public static class Cache {
        /** 是否启用缓存 */
        private boolean enabled = true;
        /** 最大条目数 */
        private int maxSize = 100;
        /** 缓存 TTL（秒） */
        private int ttl = 300;
        /** 启动时预热的高频输入列表 */
        private List<String> warmupInputs = new ArrayList<>();
    }

    @Data
    public static class Ab {
        /** A/B 测试采样率 [0.0, 1.0] */
        private double sampleRate = 1.0;
    }
}