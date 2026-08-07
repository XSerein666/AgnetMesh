package com.agentmesh.core.tool.marketplace.model;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "spring.agentmesh.marketplace")
public class MarketplaceProperties {

    /** 是否启用工具市场 */
    private boolean enabled = false;

    /** JSON 持久化数据目录 */
    private String dataDir = System.getProperty("user.dir") + "/data/tool-marketplace";

    /** API Key 配置 */
    private ApiKeys apiKeys = new ApiKeys();

    /** 内部服务 Token */
    private String internalToken;

    /** 内部执行端点速率限制（次/分钟） */
    private int internalExecuteRateLimit = 100;

    @Data
    public static class ApiKeys {
        private String publisher;
        private String admin;
    }
}
