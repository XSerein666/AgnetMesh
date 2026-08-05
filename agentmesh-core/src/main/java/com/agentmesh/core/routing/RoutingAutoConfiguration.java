package com.agentmesh.core.routing;

import com.agentmesh.core.infrastructure.AgentMeshMetrics;
import com.agentmesh.core.llm.LlmClient;
import com.agentmesh.core.registry.AgentRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(RoutingProperties.class)
public class RoutingAutoConfiguration {

    /** 关键词路由（默认） */
    @Bean
    @ConditionalOnMissingBean
    public KeywordRoutingStrategy keywordRoutingStrategy() {
        return new KeywordRoutingStrategy();
    }

    /**
     * LLM 路由策略。
     * 当 strategy=llm 或 strategy=ab 时创建（ab 模式下 LLM 作为影子策略）。
     *
     * 注意：@ConditionalOnExpression 字符串硬编码属性名，无 IDE 校验；
     * 后续可改为自定义 Condition 类。
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnExpression("'${agentmesh.routing.strategy:keyword}'.equals('llm') || '${agentmesh.routing.strategy:keyword}'.equals('ab')")
    public LlmRoutingStrategy llmRoutingStrategy(LlmClient llmClient,
                                                  KeywordRoutingStrategy fallback,
                                                  RoutingProperties properties,
                                                  AgentMeshMetrics metrics) {
        RoutingProperties.Llm llm = properties.getLlm();
        RoutingProperties.Cache cacheCfg = llm.getCache();
        RoutingCache routingCache = cacheCfg.isEnabled()
                ? new RoutingCache(cacheCfg.getMaxSize(), Duration.ofSeconds(cacheCfg.getTtl()))
                : new RoutingCache(0, Duration.ZERO);
        return new LlmRoutingStrategy(llmClient, fallback,
                llm.getTopK(), llm.getSkipRecallThreshold(),
                llm.getConfidenceThreshold(),
                Duration.ofSeconds(llm.getTimeout()),
                metrics, routingCache);
    }

    /**
     * 根据 strategy 配置选择 RoutingStrategy。
     * 配置错误处理：
     * - strategy=llm 但 llmStrategy==null → WARN + 抛 IllegalStateException
     * - strategy=ab 但 llmStrategy==null → WARN + 抛 IllegalStateException
     */
    @Bean
    @ConditionalOnMissingBean(name = "routingStrategy")
    public RoutingStrategy routingStrategy(
            KeywordRoutingStrategy keywordStrategy,
            @Autowired(required = false) LlmRoutingStrategy llmStrategy,
            RoutingProperties properties,
            AgentMeshMetrics metrics) {
        if ("ab".equals(properties.getStrategy())) {
            if (llmStrategy == null) {
                log.error("[Routing] strategy=ab 但 LLM 路由策略不可用（LlmClient 缺失或配置错误），"
                        + "A/B 测试无法启动");
                throw new IllegalStateException(
                        "A/B 路由策略需要 LLM 路由支持，请检查 LlmClient 配置");
            }
            log.info("[Routing] 使用 A/B 测试路由策略 (keyword 主 + LLM 影子)");
            return new AbRoutingStrategy(keywordStrategy, llmStrategy,
                    metrics, properties.getAb().getSampleRate());
        }
        if ("llm".equals(properties.getStrategy())) {
            if (llmStrategy == null) {
                log.error("[Routing] strategy=llm 但 LLM 路由策略不可用（LlmClient 缺失或配置错误）");
                throw new IllegalStateException(
                        "LLM 路由策略需要 LlmClient，请检查 agentmesh.llm 配置");
            }
            log.info("[Routing] 使用 LLM 路由策略");
            return llmStrategy;
        }
        log.info("[Routing] 使用关键词路由策略");
        return keywordStrategy;
    }

    // ========== Phase 13 新增 Bean ==========

    /** Agent 描述自动生成器 */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnExpression("'${agentmesh.routing.strategy:keyword}'.equals('llm') || '${agentmesh.routing.strategy:keyword}'.equals('ab')")
    public AgentDescriptionGenerator agentDescriptionGenerator(LlmClient llmClient) {
        return new AgentDescriptionGenerator(llmClient, Duration.ofSeconds(10));
    }

    /** descGen 专用线程池 */
    @Bean
    @ConditionalOnMissingBean(name = "descGenExecutor")
    @ConditionalOnExpression("'${agentmesh.routing.strategy:keyword}'.equals('llm') || '${agentmesh.routing.strategy:keyword}'.equals('ab')")
    public ExecutorService descGenExecutor() {
        return Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "descGen");
            t.setDaemon(true);
            return t;
        });
    }

    /** 缓存预热器（仅 warmup-inputs 非空时创建） */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnExpression("'${agentmesh.routing.strategy:keyword}'.equals('llm') || '${agentmesh.routing.strategy:keyword}'.equals('ab')")
    @ConditionalOnProperty(name = "agentmesh.routing.llm.cache.warmup-inputs")
    public RoutingCacheWarmer routingCacheWarmer(
            RoutingStrategy llmStrategy,
            AgentRegistry agentRegistry,
            RoutingProperties properties) {
        return new RoutingCacheWarmer(llmStrategy, agentRegistry,
                properties.getLlm().getCache().getWarmupInputs());
    }
}