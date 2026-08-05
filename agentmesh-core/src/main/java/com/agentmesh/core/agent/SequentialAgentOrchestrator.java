package com.agentmesh.core.agent;

import com.agentmesh.core.infrastructure.TraceIdContext;
import com.agentmesh.core.llm.StreamEvent;
import com.agentmesh.core.remote.AgentClient;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 顺序编排器：按 plan 中 Agent 的顺序依次执行。
 * 每个 Agent 执行完 ReAct 循环后，将最终文本输出作为下一个 Agent 的输入。
 *
 * 传参方式：使用 Flux.defer 延迟求值，确保上一步的 TEXT 输出在订阅时才作为下一步输入。
 *
 * 本地/远程分支：
 * - 本地 Agent（agentUrl 为空）：通过 ReActAgentFactory 创建实例，本地执行 ReAct 循环
 * - 远程 Agent（agentUrl 非空）：通过 AgentClient.callSkillAsync() 异步调用，结果包装为 TEXT 事件
 *   注意：远程 Agent 的中间事件（thinking/tool_call）暂不中继，Phase 8 实现 SSE 透传
 */
@Slf4j
public class SequentialAgentOrchestrator implements AgentOrchestrator {

    /**
     * 本地 Agent 工厂：输入 AgentConfig 创建 ReActAgent 实例。
     * 由业务模块注入，避免 core 直接依赖 ReActAgent 的具体构造。
     */
    @FunctionalInterface
    public interface ReActAgentFactory {
        ReActAgent create(AgentConfig config);
    }

    private final ReActAgentFactory agentFactory;
    private final AgentClient agentClient;

    public SequentialAgentOrchestrator(ReActAgentFactory agentFactory, AgentClient agentClient) {
        this.agentFactory = agentFactory;
        this.agentClient = agentClient;
    }

    /**
     * 同步编排入口。
     * 注意：内部使用 .block() 阻塞等待，仅限非反应式线程（如 main 线程）调用。
     * 在 WebFlux 上下文中请使用 orchestrateStream()。
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
        log.info("[SequentialOrchestrator] 顺序编排启动, agentCount={}, traceId={}", agents.size(), traceId);

        return executeChain(agents, 0, input, traceId);
    }

    /**
     * 递归链式执行：当前 Agent 完成后，将其输出作为下一个 Agent 的输入。
     * 使用 Flux.defer 确保下一步的 input 在订阅时才求值，而非在链构建时。
     */
    private Flux<StreamEvent> executeChain(List<AgentConfig> agents, int index, String input, String traceId) {
        if (index >= agents.size()) {
            log.info("[SequentialOrchestrator] 顺序编排完成, traceId={}", traceId);
            return Flux.just(StreamEvent.builder()
                    .type(StreamEvent.Type.DONE)
                    .build());
        }

        AgentConfig config = agents.get(index);
        String label = !config.getAgentId().isEmpty() ? config.getAgentId() : "unnamed";
        log.info("[SequentialOrchestrator] Step {}/{}: agentId={}, remote={}, traceId={}",
                index + 1, agents.size(), label, config.isRemote(), traceId);

        Flux<StreamEvent> agentEvents;
        if (config.isRemote()) {
            agentEvents = executeRemoteAgent(config, input, traceId);
        } else {
            agentEvents = executeLocalAgent(config, input, traceId);
        }

        // 用数组捕获上一步的最终输出，Flux.defer 确保在订阅时求值
        String[] capturedOutput = {input};

        return agentEvents
                .doOnNext(event -> {
                    if (event.getType() == StreamEvent.Type.TEXT) {
                        capturedOutput[0] = event.getContent();
                    }
                })
                .concatWith(Flux.defer(() -> executeChain(agents, index + 1, capturedOutput[0], traceId)));
    }

    /** 本地 Agent：通过 ReActAgentFactory 创建实例，执行 ReAct 循环 */
    private Flux<StreamEvent> executeLocalAgent(AgentConfig config, String input, String traceId) {
        String stepLabel = "[Step:" + config.getAgentId() + "] ";
        ReActAgent agent = agentFactory.create(config);
        return agent.runStream(config.resolveSystemPrompt(), input, Collections.emptyList())
                .map(event -> prefixEvent(event, stepLabel));
    }

    /**
     * 远程 Agent：通过 AgentClient.callSkillAsync() 异步调用。
     * 注意：当前仅返回最终结果，远程 Agent 的中间事件（thinking/tool_call）暂不中继。
     * Phase 8 将实现 WebClient 直连远程 /chat/stream 端点完成 SSE 透传。
     */
    @SuppressWarnings("unchecked")
    private Flux<StreamEvent> executeRemoteAgent(AgentConfig config, String input, String traceId) {
        String agentUrl = config.getAgentUrl();
        String skillId = config.getAgentId(); // 远程 Agent 的 agentId 即为其 skill 标识
        String stepLabel = "[Step:" + config.getAgentId() + "] ";

        log.info("[SequentialOrchestrator] 远程调用: agentId={}, url={}, traceId={}", skillId, agentUrl, traceId);

        return agentClient.callSkillAsync(agentUrl, skillId, Map.of("message", input), skillId)
                .flatMapMany(result -> {
                    if (result instanceof Map<?, ?> errorMap && errorMap.containsKey("error")) {
                        return Flux.just(StreamEvent.builder()
                                .type(StreamEvent.Type.ERROR)
                                .content(stepLabel + "远程调用失败: " + errorMap.get("error"))
                                .build());
                    }
                    String text = result instanceof String ? (String) result
                            : result instanceof Map ? result.toString() : String.valueOf(result);
                    return Flux.just(StreamEvent.builder()
                            .type(StreamEvent.Type.TEXT)
                            .content(stepLabel + text)
                            .build());
                });
    }

    /** 给事件内容加上步骤前缀，便于客户端区分 */
    private StreamEvent prefixEvent(StreamEvent event, String prefix) {
        if (event.getType() == StreamEvent.Type.TEXT && event.getContent() != null) {
            return StreamEvent.builder()
                    .type(event.getType())
                    .content(prefix + event.getContent())
                    .toolName(event.getToolName())
                    .toolCallId(event.getToolCallId())
                    .arguments(event.getArguments())
                    .result(event.getResult())
                    .build();
        }
        return event;
    }
}