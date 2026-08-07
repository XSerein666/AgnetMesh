package com.agentmesh.core.registry;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Agent 间鉴权配置。
 * 简单 API Key 模式：每个 Agent 配置一个 api-key，
 * 请求时通过 X-Agent-Key 头传递，被请求方校验一致性。
 */
@Data
@ConfigurationProperties(prefix = "agentmesh.auth")
public class AgentAuthProperties {

    /** 是否启用鉴权，默认 false（关闭时所有请求放行） */
    private boolean enabled = false;

    /** Agent 间通信的 API Key */
    private String apiKey = "";
}
