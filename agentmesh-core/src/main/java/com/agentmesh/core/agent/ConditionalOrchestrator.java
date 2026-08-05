package com.agentmesh.core.agent;

import com.agentmesh.core.infrastructure.TraceIdContext;
import com.agentmesh.core.llm.StreamEvent;
import com.agentmesh.core.remote.AgentClient;
import com.agentmesh.core.routing.RankedAgent;
import com.agentmesh.core.routing.RoutingStrategy;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

import java.net.ConnectException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * 条件路由编排器：根据用户意图选择最合适的 Agent 执行。
 * 路由策略由注入的 RoutingStrategy 决定（默认关键词匹配，可切换 LLM 路由）。
 *
 * 执行层语义：
 * - 默认执行路由榜首 Agent
 * - 余下名次作为 failover 链（仅 retryable=true 的 Agent 启用）
 * - failover 仅对可重试错误（超时/连接失败/5xx）触发
 *
 * 本地/远程分支：
 * - 本地 Agent（agentUrl 为空）：通过 ReActAgentFactory 创建实例，本地执行 ReAct 循环
 * - 远程 Agent（agentUrl 非空）：通过 AgentClient.callSkillAsync() 异步调用
 */
@Slf4j
public class ConditionalOrchestrator implements AgentOrchestrator {

    private final SequentialAgentOrchestrator.ReActAgentFactory agentFactory;
    private final AgentClient agentClient;
    private final RoutingStrategy routingStrategy;

    public ConditionalOrchestrator(SequentialAgentOrchestrator.ReActAgentFactory agentFactory,
                                   AgentClient agentClient,
                                   RoutingStrategy routingStrategy) {
        this.agentFactory = agentFactory;
        this.agentClient = agentClient;
        this.routingStrategy = routingStrategy;
    }

    /**
     * 同步编排入口。
     * 注意：内部使用 .block() 阻塞等待，仅限非反应式线程调用。
     */
    @Override
    public OrchestrationResult orchestrate(OrchestrationPlan plan, String input) {
        return orchestrateStream(plan, input)
                .collectList()
                .map(events -> {
                    OrchestrationResult result = new OrchestrationResult();
                    result.setSuccess(true);
                    String finalOutput = events.stream()
                            .filter(e -> e.getType() == StreamEvent.Type.TEXT)
                            .reduce((first, second) -> second)
                            .map(StreamEvent::getContent)
                            .orElse("");
                    result.setFinalOutput(finalOutput);
                    return result;
                })
                .block(); // 仅限非反应式线程调用
    }

    @Override
    public Flux<StreamEvent> orchestrateStream(OrchestrationPlan plan, String input) {
        List<AgentConfig> agents = plan.getAgents();
        if (agents.isEmpty()) {
            return Flux.just(StreamEvent.builder()
                    .type(StreamEvent.Type.ERROR)
                    .content("编排计划中没有 Agent")
                    .build());
        }

        String traceId = TraceIdContext.get();
        List<RankedAgent> ranked = routingStrategy.route(input, agents);

        // 路由为空时兜底：取第一个候选
        if (ranked.isEmpty()) {
            log.warn("[ConditionalOrchestrator] 路由无结果，使用首个候选兜底, traceId={}", traceId);
            ranked = List.of(RankedAgent.builder()
                    .agent(agents.get(0)).confidence(0.0).build());
        }

        log.info("[ConditionalOrchestrator] 路由结果: topAgent={}, confidence={}, totalRanked={}, traceId={}",
                ranked.get(0).getAgent().getAgentId(), ranked.get(0).getConfidence(),
                ranked.size(), traceId);

        return executeWithFailover(ranked, input, traceId, 0);
    }

    /** 递归执行 + failover。入口已判空，ranked 必有至少 1 个元素。 */
    private Flux<StreamEvent> executeWithFailover(List<RankedAgent> ranked, String input,
                                                   String traceId, int index) {
        // 防御：已遍历完所有候选
        if (index >= ranked.size()) {
            return Flux.just(StreamEvent.builder()
                    .type(StreamEvent.Type.ERROR)
                    .content("所有候选 Agent 执行失败")
                    .build());
        }

        RankedAgent current = ranked.get(index);
        AgentConfig config = current.getAgent();

        Flux<StreamEvent> agentFlux;
        if (config.isRemote()) {
            agentFlux = executeRemoteAgent(config, input, traceId);
        } else {
            ReActAgent agent = agentFactory.create(config);
            agentFlux = agent.runStream(config.resolveSystemPrompt(), input, Collections.emptyList());
        }

        // 不满足 failover 条件：当前 Agent 不可重试，或已是最后一个
        if (!current.getAgent().isRetryable() || index >= ranked.size() - 1) {
            return agentFlux;
        }

        // failover：仅对首事件前的可重试错误触发，流中途断只发 errorEvent
        AtomicBoolean firstEventReceived = new AtomicBoolean(false);
        return agentFlux
                .doOnNext(event -> firstEventReceived.set(true))
                .onErrorResume(e -> {
                    if (firstEventReceived.get()) {
                        log.warn("[ConditionalOrchestrator] 流中途失败，不触发 failover: agent={}, error={}, traceId={}",
                                current.getAgent().getAgentId(), e.getMessage(), traceId);
                        return Flux.just(StreamEvent.builder()
                                .type(StreamEvent.Type.ERROR)
                                .content("Agent 流执行中断: " + e.getMessage())
                                .build());
                    }
                    if (!isRetryableError(e)) {
                        return Flux.error(e); // 业务错误不透传 failover
                    }
                    log.warn("[ConditionalOrchestrator] 可重试错误, failover: failed={}, next={}, error={}, traceId={}",
                            current.getAgent().getAgentId(),
                            ranked.get(index + 1).getAgent().getAgentId(),
                            e.getMessage(), traceId);
                    return executeWithFailover(ranked, input, traceId, index + 1);
                });
    }

    /**
     * 可重试错误：仅按异常类型判断，不使用字符串匹配（避免误伤业务文本）。
     * 4xx、业务错误等不应触发 failover。
     */
    private boolean isRetryableError(Throwable e) {
        Throwable cause = e;
        while (cause != null) {
            if (cause instanceof TimeoutException) return true;
            if (cause instanceof ConnectException) return true;
            if (cause instanceof WebClientResponseException wcre) {
                return wcre.getStatusCode().is5xxServerError();
            }
            cause = cause.getCause();
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private Flux<StreamEvent> executeRemoteAgent(AgentConfig config, String input, String traceId) {
        String agentUrl = config.getAgentUrl();
        String skillId = config.getAgentId();

        log.info("[ConditionalOrchestrator] 远程调用: agentId={}, url={}, traceId={}", skillId, agentUrl, traceId);

        return agentClient.callSkillAsync(agentUrl, skillId, Map.of("message", input), skillId)
                .flatMapMany(result -> {
                    if (result instanceof Map<?, ?> errorMap && errorMap.containsKey("error")) {
                        return Flux.just(StreamEvent.builder()
                                .type(StreamEvent.Type.ERROR)
                                .content("远程调用失败: " + errorMap.get("error"))
                                .build());
                    }
                    String text = result instanceof String ? (String) result
                            : result instanceof Map ? result.toString() : String.valueOf(result);
                    return Flux.just(StreamEvent.builder()
                            .type(StreamEvent.Type.TEXT)
                            .content(text)
                            .build(),
                            StreamEvent.builder().type(StreamEvent.Type.DONE).build());
                });
    }
}