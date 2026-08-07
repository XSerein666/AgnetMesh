package com.agentmesh.core.agent;

import com.agentmesh.core.infrastructure.AgentMeshMetrics;
import com.agentmesh.core.llm.StreamEvent;
import com.agentmesh.core.remote.AgentClient;
import com.agentmesh.core.routing.RankedAgent;
import com.agentmesh.core.routing.RoutingStrategy;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 编排器 Metrics 测试。
 * 验证：
 *   1. Sequential 成功 → recordOrchestration("sequential", "SUCCESS")
 *   2. Sequential 失败 → recordOrchestration("sequential", "FAILED")
 *   3. Conditional failover → recordFailover 调用
 *   4. metrics=null → 降级不抛异常
 */
class OrchestrationMetricsTest {

    @Test
    void sequentialSuccess_shouldRecordSuccessMetric() {
        ReActAgent mockAgent = mock(ReActAgent.class);
        when(mockAgent.runStream(any(), any(), any())).thenReturn(
                Flux.just(StreamEvent.builder().type(StreamEvent.Type.TEXT).content("ok").build())
        );

        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AgentMeshMetrics metrics = new AgentMeshMetrics(registry);
        SequentialAgentOrchestrator.ReActAgentFactory factory = config -> mockAgent;

        SequentialAgentOrchestrator orchestrator =
                new SequentialAgentOrchestrator(factory, mock(AgentClient.class), metrics);

        OrchestrationPlan plan = SimpleOrchestrationPlan.builder()
                .addAgent(AgentConfig.builder().agentId("agent-1").build())
                .build();

        orchestrator.orchestrateStream(plan, "test").blockLast();

        assertThat(registry.counter("agentmesh.orchestration.invocations",
                "orchestrator", "sequential", "status", "SUCCESS").count())
                .isEqualTo(1.0);
    }

    @Test
    void sequentialFailure_shouldRecordFailedMetric() {
        ReActAgent mockAgent = mock(ReActAgent.class);
        when(mockAgent.runStream(any(), any(), any())).thenReturn(
                Flux.error(new RuntimeException("agent error"))
        );

        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AgentMeshMetrics metrics = new AgentMeshMetrics(registry);
        SequentialAgentOrchestrator.ReActAgentFactory factory = config -> mockAgent;

        SequentialAgentOrchestrator orchestrator =
                new SequentialAgentOrchestrator(factory, mock(AgentClient.class), metrics);

        OrchestrationPlan plan = SimpleOrchestrationPlan.builder()
                .addAgent(AgentConfig.builder().agentId("agent-1").build())
                .build();

        orchestrator.orchestrateStream(plan, "test").onErrorResume(e -> Flux.empty()).blockLast();

        assertThat(registry.counter("agentmesh.orchestration.invocations",
                "orchestrator", "sequential", "status", "FAILED").count())
                .isEqualTo(1.0);
    }

    @Test
    void conditionalFailover_shouldRecordFailoverMetric() {
        ReActAgent failAgent = mock(ReActAgent.class);
        when(failAgent.runStream(any(), any(), any())).thenReturn(
                Flux.error(new TimeoutException("timeout"))
        );
        ReActAgent okAgent = mock(ReActAgent.class);
        when(okAgent.runStream(any(), any(), any())).thenReturn(
                Flux.just(StreamEvent.builder().type(StreamEvent.Type.TEXT).content("recovered").build())
        );

        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AgentMeshMetrics metrics = new AgentMeshMetrics(registry);

        // 第一个工厂返回 failAgent，第二个返回 okAgent
        SequentialAgentOrchestrator.ReActAgentFactory factory = config ->
                "agent-fail".equals(config.getAgentId()) ? failAgent : okAgent;

        RoutingStrategy routingStrategy = (input, candidates) -> List.of(
                RankedAgent.builder()
                        .agent(AgentConfig.builder().agentId("agent-fail").retryable(true).build())
                        .confidence(0.9).build(),
                RankedAgent.builder()
                        .agent(AgentConfig.builder().agentId("agent-ok").retryable(true).build())
                        .confidence(0.5).build()
        );

        ConditionalOrchestrator orchestrator = new ConditionalOrchestrator(
                factory, mock(AgentClient.class), routingStrategy, metrics);

        OrchestrationPlan plan = SimpleOrchestrationPlan.builder()
                .addAgent(AgentConfig.builder().agentId("agent-fail").retryable(true).build())
                .addAgent(AgentConfig.builder().agentId("agent-ok").retryable(true).build())
                .build();

        orchestrator.orchestrateStream(plan, "test").blockLast();

        assertThat(registry.counter("agentmesh.orchestration.failover",
                "failed_agent", "agent-fail", "next_agent", "agent-ok").count())
                .isEqualTo(1.0);
    }

    @Test
    void metricsNull_shouldDegradeWithoutException() {
        ReActAgent mockAgent = mock(ReActAgent.class);
        when(mockAgent.runStream(any(), any(), any())).thenReturn(
                Flux.just(StreamEvent.builder().type(StreamEvent.Type.TEXT).content("ok").build())
        );

        SequentialAgentOrchestrator.ReActAgentFactory factory = config -> mockAgent;

        // metrics = null
        SequentialAgentOrchestrator orchestrator =
                new SequentialAgentOrchestrator(factory, mock(AgentClient.class), null);

        OrchestrationPlan plan = SimpleOrchestrationPlan.builder()
                .addAgent(AgentConfig.builder().agentId("agent-1").build())
                .build();

        // 不应抛出 NPE
        var events = orchestrator.orchestrateStream(plan, "test").collectList().block();
        assertThat(events).isNotNull();
        assertThat(events.stream().anyMatch(e -> e.getType() == StreamEvent.Type.TEXT)).isTrue();
    }
}
