package com.agentmesh.core.collaboration;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import lombok.Data;

/**
 * Phase 1 自动配置：MessageBus + SharedContext + CollaborationMetrics。
 * 仅配置基础协作设施，不依赖 SupervisedOrchestrator 等 Phase 2+ 组件。
 */
@AutoConfiguration
@EnableConfigurationProperties(MessageBusAutoConfiguration.CollaborationProperties.class)
public class MessageBusAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public CollaborationMetrics collaborationMetrics(MeterRegistry meterRegistry) {
        return new CollaborationMetrics(meterRegistry);
    }

    @Bean
    @ConditionalOnMissingBean(MessageBus.class)
    public InMemoryMessageBus inMemoryMessageBus(CollaborationProperties props,
                                                  ObjectProvider<CollaborationMetrics> metricsProvider) {
        return new InMemoryMessageBus(props.getMessageBufferSize(), metricsProvider.getIfAvailable());
    }

    @Data
    @ConfigurationProperties(prefix = "agentmesh.collaboration")
    public static class CollaborationProperties {
        /** 消息总线背压缓冲区大小，默认 256 */
        private int messageBufferSize = 256;
        /** requestReply 默认超时时间（秒），默认 30 */
        private int requestTimeout = 30;
    }
}
