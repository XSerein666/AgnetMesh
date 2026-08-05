package com.agentmesh.core.memory;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 记忆模块配置
 */
@Data
@ConfigurationProperties(prefix = "agentmesh.memory")
public class MemoryProperties {

    /** 短期记忆配置 */
    private ShortTerm shortTerm = new ShortTerm();

    /** 长期记忆配置 */
    private LongTerm longTerm = new LongTerm();

    @Data
    public static class ShortTerm {
        /** 滑动窗口保留最近消息数 */
        private int windowSize = 6;
        /** 消息数超过此阈值触发摘要压缩 */
        private int summaryThreshold = 10;
        /** 摘要使用的模型（默认使用低成本模型） */
        private String summaryModel = "qwen-turbo";
        /** 摘要 Token 上限 */
        private int maxSummaryTokens = 300;
    }

    @Data
    public static class LongTerm {
        /** 是否启用长期记忆 */
        private boolean enabled = false;
        /** 存储实现：in-memory | pgvector */
        private String store = "in-memory";
        /** 提取配置 */
        private Extraction extraction = new Extraction();
        /** 检索配置 */
        private Retrieval retrieval = new Retrieval();

        @Data
        public static class Extraction {
            /** 对话结束后自动提取 */
            private boolean auto = true;
            /** 提取使用的模型 */
            private String model = "qwen-turbo";
            /** 每次最多提取记忆条数 */
            private int maxItemsPerTurn = 3;
        }

        @Data
        public static class Retrieval {
            /** 检索返回 Top-K 条 */
            private int topK = 5;
            /** 相似度最低阈值 */
            private double minScore = 0.6;
        }
    }
}