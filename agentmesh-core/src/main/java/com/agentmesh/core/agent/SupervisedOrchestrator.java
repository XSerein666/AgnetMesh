package com.agentmesh.core.agent;

import com.agentmesh.core.collaboration.AgentCollaboration;
import com.agentmesh.core.collaboration.MessageBus;
import com.agentmesh.core.collaboration.SharedContext;
import com.agentmesh.core.infrastructure.AgentMeshMetrics;
import com.agentmesh.core.infrastructure.TraceIdContext;
import com.agentmesh.core.llm.StreamEvent;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Supervisor-Worker 编排器。
 *
 * 作为 AgentOrchestrator 的实现，将 Supervisor-Worker 协作模式集成到现有的编排器体系中。
 * 通过 ExecutionMode.SUPERVISED 启用。
 *
 * 核心流程：委托给 SupervisorWorkerCollaboration 执行协作逻辑。
 */
@Slf4j
public class SupervisedOrchestrator implements AgentOrchestrator {

    private final AgentCollaboration collaboration;
    private final MessageBus messageBus;
    private final AgentMeshMetrics metrics;
    private final Duration requestTimeout;

    public SupervisedOrchestrator(AgentCollaboration collaboration,
                                   MessageBus messageBus,
                                   AgentMeshMetrics metrics,
                                   Duration requestTimeout) {
        this.collaboration = collaboration;
        this.messageBus = messageBus;
        this.metrics = metrics;
        this.requestTimeout = requestTimeout;
    }

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
                .block();
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
        String collaborationId = UUID.randomUUID().toString().substring(0, 8);
        log.info("[SupervisedOrchestrator] Supervisor-Worker 编排启动, collaborationId={}, agentCount={}, traceId={}",
                collaborationId, agents.size(), traceId);

        Timer.Sample sample = metrics != null ? metrics.startOrchestrationTimer() : null;
        String orchestratorType = "supervised";

        // 创建与 collaborationId 绑定的共享上下文
        SharedContext sharedContext = new SharedContext(collaborationId);

        // 将 sharedContext 和 messageBus 注入到 AgentConfig 中
        for (AgentConfig agent : agents) {
            agent.setSharedContext(sharedContext);
            agent.setMessageBus(messageBus);
        }

        return collaboration.collaborate(agents, input, sharedContext, messageBus, collaborationId)
                .doOnComplete(() -> recordOrchestrationComplete(orchestratorType, "SUCCESS", sample))
                .doOnError(e -> {
                    log.error("[SupervisedOrchestrator] 编排异常, collaborationId={}, traceId={}",
                            collaborationId, traceId, e);
                    recordOrchestrationComplete(orchestratorType, "FAILED", sample);
                })
                .doFinally(signal -> {
                    sharedContext.clear();
                    log.info("[SupervisedOrchestrator] 编排结束, collaborationId={}, signal={}",
                            collaborationId, signal);
                });
    }

    private void recordOrchestrationComplete(String orchestratorType, String status, Timer.Sample sample) {
        if (metrics == null) {
            return;
        }
        metrics.recordOrchestration(orchestratorType, status);
        if (sample != null) {
            metrics.stopOrchestrationTimer(sample, orchestratorType);
        }
    }
}
