package com.agentmesh.core.registry;

import com.agentmesh.core.agent.ConditionalOrchestrator;
import com.agentmesh.core.agent.ParallelOrchestrator;
import com.agentmesh.core.agent.SequentialAgentOrchestrator;
import com.agentmesh.core.infrastructure.AgentMeshMetrics;
import com.agentmesh.core.infrastructure.TraceIdFilter;
import com.agentmesh.core.protocol.AgentCard;
import com.agentmesh.core.remote.AgentClient;
import com.agentmesh.core.remote.HttpAgentClient;
import com.agentmesh.core.remote.RemoteToolProperties;
import com.agentmesh.core.remote.RemoteToolRegistrar;
import com.agentmesh.core.routing.AgentDescriptionGenerator;
import com.agentmesh.core.routing.RoutingStrategy;
import com.agentmesh.core.tool.ToolRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.concurrent.ExecutorService;

@Slf4j
@AutoConfiguration
@EnableConfigurationProperties({AgentRegistryProperties.class, RemoteToolProperties.class,
        AgentAuthProperties.class})
public class AgentRegistryAutoConfiguration {

    /**
     * 创建带超时的 RestTemplate，供 AgentRegistry 使用
     */
    @Bean
    @ConditionalOnMissingBean(name = "agentRegistryRestTemplate")
    public RestTemplate agentRegistryRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(10));
        return new RestTemplate(factory);
    }

    /**
     * WebClient Bean（供异步 HTTP 调用使用）
     */
    @Bean
    @ConditionalOnMissingBean
    public WebClient agentRegistryWebClient() {
        return WebClient.builder()
                .codecs(config -> config.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
                .build();
    }

    /**
     * 创建 AgentRegistry。
     * 通过 ObjectProvider 延迟获取 AgentCard Bean（由业务模块提供并填充 skills），
     * 保证自身节点与 /.well-known/agent.json 返回的是同一个对象。
     */
    @Bean
    @ConditionalOnMissingBean
    public AgentRegistry agentRegistry(
            ObjectProvider<AgentCard> agentCardProvider,
            RestTemplate agentRegistryRestTemplate,
            AgentRegistryProperties properties,
            ObjectProvider<AgentDescriptionGenerator> descriptionGeneratorProvider,
            ObjectProvider<ExecutorService> descGenExecutorProvider) {

        AgentCard selfCard = agentCardProvider.getIfAvailable(() -> {
            log.warn("[AgentRegistry] 未找到 AgentCard Bean，使用配置中的默认名片（无 skills）");
            return properties.getSelf().toAgentCard();
        });

        AgentDescriptionGenerator descGen = descriptionGeneratorProvider.getIfAvailable();
        ExecutorService descGenExec = descGenExecutorProvider.getIfAvailable();
        return new InMemoryAgentRegistry(selfCard, agentRegistryRestTemplate,
                properties.getPeers(), descGen, descGenExec);
    }

    // ========== Phase 6：远程调用 ==========

    /**
     * 远程调用客户端（HTTP 实现）。
     * Phase 7：注入 WebClient 以支持异步调用。
     */
    @Bean
    @ConditionalOnMissingBean
    public AgentClient agentClient(RestTemplate agentRegistryRestTemplate,
                                    WebClient agentRegistryWebClient,
                                    RemoteToolProperties properties,
                                    AgentAuthProperties authProperties,
                                    AgentRegistryProperties registryProperties,
                                    AgentMeshMetrics metrics) {
        String selfAgentId = registryProperties.getSelf() != null
                ? registryProperties.getSelf().getAgentId() : "";
        return new HttpAgentClient(agentRegistryRestTemplate,
                agentRegistryWebClient, properties, authProperties, selfAgentId, metrics);
    }

    /**
     * 远程工具注册器。
     */
    @Bean
    @ConditionalOnMissingBean
    public RemoteToolRegistrar remoteToolRegistrar(AgentRegistry agentRegistry,
                                                    AgentClient agentClient) {
        return new RemoteToolRegistrar(agentRegistry, agentClient);
    }

    /**
     * 装配 refreshListener：在 AgentRegistry 刷新后自动重新导入远程工具。
     * 通过 addRefreshListener 机制解耦，core 不反向依赖 demo 的 ToolRegistry Bean。
     * 注意：不使用 @ConditionalOnMissingBean，因为返回类型为 Object 会导致永远被跳过
     * （容器中已有大量 Object 类型 Bean）。业务模块可通过覆盖同名 Bean 来替换。
     */
    @Bean
    public Object remoteToolRefreshListener(
            AgentRegistry agentRegistry,
            RemoteToolRegistrar registrar,
            ToolRegistry toolRegistry) {
        if (agentRegistry instanceof InMemoryAgentRegistry inMemory) {
            inMemory.addRefreshListener(() -> {
                try {
                    registrar.importAll(toolRegistry);
                } catch (Exception e) {
                    log.warn("[AgentRegistry] refreshListener 执行异常（不影响 registry 自身刷新）: {}",
                            e.getMessage());
                }
            });
        }
        return new Object(); // 占位 Bean，确保装配执行
    }

    // ========== Phase 7：Agent 编排 ==========

    /**
     * 顺序编排器。
     * ReActAgentFactory 由业务模块注入，AgentClient 用于远程 Agent 调用。
     */
    @Bean
    @ConditionalOnMissingBean
    public SequentialAgentOrchestrator sequentialAgentOrchestrator(
            SequentialAgentOrchestrator.ReActAgentFactory agentFactory,
            AgentClient agentClient,
            AgentMeshMetrics metrics) {
        return new SequentialAgentOrchestrator(agentFactory, agentClient, metrics);
    }

    /**
     * 条件路由编排器（Phase 11：注入 RoutingStrategy）。
     */
    @Bean
    @ConditionalOnMissingBean
    public ConditionalOrchestrator conditionalOrchestrator(
            SequentialAgentOrchestrator.ReActAgentFactory agentFactory,
            AgentClient agentClient,
            RoutingStrategy routingStrategy,
            AgentMeshMetrics metrics) {
        return new ConditionalOrchestrator(agentFactory, agentClient, routingStrategy, metrics);
    }

    /**
     * 并行编排器（Phase 9）。
     */
    @Bean
    @ConditionalOnMissingBean
    public ParallelOrchestrator parallelOrchestrator(
            SequentialAgentOrchestrator.ReActAgentFactory agentFactory,
            AgentClient agentClient,
            AgentMeshMetrics metrics) {
        return new ParallelOrchestrator(agentFactory, agentClient, metrics);
    }

    // ========== Phase 9：Agent 间鉴权 ==========

    /**
     * Agent 间 API Key 鉴权过滤器。
     * 拦截 /a2a/** 路径，仅当 agentmesh.auth.enabled=true 时生效。
     */
    @Bean
    @ConditionalOnProperty(name = "agentmesh.auth.enabled", havingValue = "true")
    public FilterRegistrationBean<AgentAuthFilter> agentAuthFilter(AgentAuthProperties authProperties) {
        FilterRegistrationBean<AgentAuthFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new AgentAuthFilter(authProperties));
        registration.addUrlPatterns("/a2a/*");
        registration.setOrder(1);
        return registration;
    }

    // ========== Phase 10：可观测性 ==========

    /**
     * traceId 注入过滤器，order=0 确保在鉴权前执行。
     */
    @Bean
    public FilterRegistrationBean<TraceIdFilter> traceIdFilter() {
        FilterRegistrationBean<TraceIdFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new TraceIdFilter());
        registration.addUrlPatterns("/*");
        registration.setOrder(0);
        return registration;
    }

    /**
     * 核心指标 Bean。
     */
    @Bean
    @ConditionalOnMissingBean
    public AgentMeshMetrics agentMeshMetrics(MeterRegistry meterRegistry) {
        return new AgentMeshMetrics(meterRegistry);
    }
}
