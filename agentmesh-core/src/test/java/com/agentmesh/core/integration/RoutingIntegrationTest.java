package com.agentmesh.core.integration;

import com.agentmesh.core.agent.AgentConfig;
import com.agentmesh.core.agent.ConditionalOrchestrator;
import com.agentmesh.core.agent.ReActAgent;
import com.agentmesh.core.agent.SequentialAgentOrchestrator;
import com.agentmesh.core.infrastructure.AgentMeshMetrics;
import com.agentmesh.core.llm.LlmClient;
import com.agentmesh.core.llm.OpenAiLlmClient;
import com.agentmesh.core.llm.adapter.OpenAiAdapter;
import com.agentmesh.core.routing.KeywordRoutingStrategy;
import com.agentmesh.core.routing.LlmRoutingStrategy;
import com.agentmesh.core.routing.RankedAgent;
import com.agentmesh.core.routing.RoutingCache;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 路由集成测试：验证关键词路由和 LLM 路由的完整流程。
 */
@DisplayName("路由集成测试")
class RoutingIntegrationTest extends BaseIntegrationTest {

    private AgentMeshMetrics metrics;
    private List<AgentConfig> agents;

    @BeforeEach
    void setUp() {
        metrics = new AgentMeshMetrics(new SimpleMeterRegistry());

        agents = List.of(
                AgentConfig.builder()
                        .agentId("weather-agent")
                        .description("天气查询助手，可以查询全国各城市的天气")
                        .routingRule("天气:0,温度:1,降雨:2")
                        .routingTags(List.of("天气", "温度", "降雨"))
                        .retryable(true)
                        .build(),
                AgentConfig.builder()
                        .agentId("search-agent")
                        .description("知识检索助手，可以搜索知识库")
                        .routingRule("搜索:0,知识:1,查询:2")
                        .routingTags(List.of("搜索", "知识", "查询"))
                        .retryable(false)
                        .build(),
                AgentConfig.builder()
                        .agentId("translator-agent")
                        .description("翻译助手，支持中英文互译")
                        .routingRule("翻译:0,英语:1,中文:2")
                        .routingTags(List.of("翻译", "英语", "中文"))
                        .build()
        );
    }

    // ========== 关键词路由 ==========

    @Test
    @DisplayName("关键词路由：天气查询 → 路由到 weather-agent")
    void keywordRouting_shouldRouteToWeatherAgent() {
        KeywordRoutingStrategy strategy = new KeywordRoutingStrategy();
        List<RankedAgent> result = strategy.route("北京今天天气怎么样", agents);

        assertThat(result).isNotEmpty();
        assertThat(result.get(0).getAgent().getAgentId()).isEqualTo("weather-agent");
    }

    @Test
    @DisplayName("关键词路由：搜索知识 → 路由到 search-agent")
    void keywordRouting_shouldRouteToSearchAgent() {
        KeywordRoutingStrategy strategy = new KeywordRoutingStrategy();
        List<RankedAgent> result = strategy.route("帮我搜索一下 AgentMesh 的相关知识", agents);

        assertThat(result).isNotEmpty();
        assertThat(result.get(0).getAgent().getAgentId()).isEqualTo("search-agent");
    }

    @Test
    @DisplayName("关键词路由：无匹配 → 返回空列表")
    void keywordRouting_noMatch_shouldReturnEmpty() {
        KeywordRoutingStrategy strategy = new KeywordRoutingStrategy();
        List<RankedAgent> result = strategy.route("今天吃什么", agents);

        assertThat(result).isEmpty();
    }

    // ========== LLM 路由 ==========

    @Test
    @DisplayName("LLM 路由：天气查询 → LLM 精排选择 weather-agent")
    void llmRouting_shouldSelectWeatherAgent() {
        llmMock.stubChatResponse("""
                [
                  {"agentId": "weather-agent", "score": 0.95},
                  {"agentId": "search-agent", "score": 0.3},
                  {"agentId": "translator-agent", "score": 0.1}
                ]
                """);

        LlmClient llmClient = new OpenAiLlmClient(
                "test-key", llmMock.baseUrl(), "test-model",
                new OpenAiAdapter(), metrics);
        RoutingCache cache = new RoutingCache(10, Duration.ofMinutes(5));

        LlmRoutingStrategy strategy = new LlmRoutingStrategy(
                llmClient, new KeywordRoutingStrategy(), 5, 10, 0.6,
                Duration.ofSeconds(5), metrics, cache);
        List<RankedAgent> result = strategy.route("北京天气怎么样", agents);

        assertThat(result).isNotEmpty();
        assertThat(result.get(0).getAgent().getAgentId()).isEqualTo("weather-agent");
    }

    @Test
    @DisplayName("LLM 路由：低置信度 → 回退到关键词路由")
    void llmRouting_lowConfidence_shouldFallbackToKeyword() {
        llmMock.stubChatResponse("""
                [
                  {"agentId": "weather-agent", "score": 0.4},
                  {"agentId": "search-agent", "score": 0.35}
                ]
                """);

        LlmClient llmClient = new OpenAiLlmClient(
                "test-key", llmMock.baseUrl(), "test-model",
                new OpenAiAdapter(), metrics);
        RoutingCache cache = new RoutingCache(10, Duration.ofMinutes(5));

        LlmRoutingStrategy strategy = new LlmRoutingStrategy(
                llmClient, new KeywordRoutingStrategy(), 5, 10, 0.6,
                Duration.ofSeconds(5), metrics, cache);
        List<RankedAgent> result = strategy.route("北京天气怎么样", agents);

        // 置信度低于阈值，回退到关键词路由
        assertThat(result).isNotEmpty();
    }

    // ========== ConditionalOrchestrator ==========

    @Test
    @DisplayName("条件编排：LLM 路由 + Failover")
    void conditionalOrchestrator_shouldUseRoutingAndFailover() {
        llmMock.stubChatResponse("""
                [
                  {"agentId": "weather-agent", "score": 0.9}
                ]
                """);

        LlmClient llmClient = new OpenAiLlmClient(
                "test-key", llmMock.baseUrl(), "test-model",
                new OpenAiAdapter(), metrics);
        RoutingCache cache = new RoutingCache(10, Duration.ofMinutes(5));
        LlmRoutingStrategy routingStrategy = new LlmRoutingStrategy(
                llmClient, new KeywordRoutingStrategy(), 5, 10, 0.6,
                Duration.ofSeconds(5), metrics, cache);

        SequentialAgentOrchestrator.ReActAgentFactory factory = config ->
                new ReActAgent(llmClient, toolRegistry, 5);

        ConditionalOrchestrator orchestrator = new ConditionalOrchestrator(
                factory, null, routingStrategy);

        assertThat(orchestrator).isNotNull();
    }
}