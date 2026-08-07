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
 * Debate 编排器。
 * 通过 ExecutionMode.DEBATE 启用。
 */
@Slf4j
public class DebateOrchestrator implements AgentOrchestrator {

    private final AgentCollaboration collaboration;
    private final MessageBus messageBus;
    private final AgentMeshMetrics metrics;
    private final Duration requestTimeout;

    public DebateOrchestrator(AgentCollaboration collaboration,
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
        log.info("[DebateOrchestrator] Debate 编排启动, collaborationId={}, agentCount={}, traceId={}",
                collaborationId, agents.size(), traceId);

        Timer.Sample sample = metrics != null ? metrics.startOrchestrationTimer() : null;
        String orchestratorType = "debate";

        SharedContext sharedContext = new SharedContext(collaborationId);

        for (AgentConfig agent : agents) {
            agent.setSharedContext(sharedContext);
            agent.setMessageBus(messageBus);
        }

        return collaboration.collaborate(agents, input, sharedContext, messageBus, collaborationId)
                .doOnComplete(() -> recordOrchestrationComplete(orchestratorType, "SUCCESS", sample))
                .doOnError(e -> {
                    log.error("[DebateOrchestrator] 编排异常, collaborationId={}, traceId={}",
                            collaborationId, traceId, e);
                    recordOrchestrationComplete(orchestratorType, "FAILED", sample);
                })
                .doFinally(signal -> {
                    sharedContext.clear();
                    log.info("[DebateOrchestrator] 编排结束, collaborationId={}, signal={}",
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
