package com.jewel.a2a.server.config;

import com.agentmesh.core.llm.LlmClient;
import com.agentmesh.core.llm.LlmChatResponse;
import com.agentmesh.core.llm.TokenEstimator;
import com.agentmesh.core.llm.ToolChoice;
import com.agentmesh.core.remote.AgentClient;
import com.agentmesh.core.routing.RoutingStrategy;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 测试用 Mock 配置，提供 AgentMesh 所需的外部依赖 Mock。
 * 避免加载真实的 LlmAutoConfiguration、数据库连接等。
 */
@TestConfiguration
public class TestMockConfig {

    @Bean
    @Primary
    public MeterRegistry meterRegistry() {
        return new SimpleMeterRegistry();
    }

    @Bean
    @Primary
    public LlmClient mockLlmClient() {
        LlmClient mock = mock(LlmClient.class);

        // 默认模拟返回
        when(mock.chat(anyList())).thenReturn("这是一个测试回复。");
        when(mock.chatWithTools(anyList(), anyList(), any(ToolChoice.class)))
                .thenReturn(LlmChatResponse.builder()
                        .content("这是一个测试回复。")
                        .finishReason("stop")
                        .build());
        when(mock.supportsFunctionCalling()).thenReturn(false);
        when(mock.supportsToolRole()).thenReturn(false);
        when(mock.getTokenEstimator()).thenReturn(new TokenEstimator() {
            @Override
            public int estimateTokens(String text) {
                return text == null ? 0 : text.length() / 2;
            }
        });

        return mock;
    }

    @Bean
    @Primary
    public AgentClient mockAgentClient() {
        return mock(AgentClient.class);
    }

    @Bean
    @Primary
    public RoutingStrategy mockRoutingStrategy() {
        return mock(RoutingStrategy.class);
    }
}