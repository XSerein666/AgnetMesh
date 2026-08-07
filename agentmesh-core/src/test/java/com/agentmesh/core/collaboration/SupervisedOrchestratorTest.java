package com.agentmesh.core.collaboration;

import com.agentmesh.core.agent.*;
import com.agentmesh.core.infrastructure.AgentMeshMetrics;
import com.agentmesh.core.llm.StreamEvent;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SupervisedOrchestrator 测试。
 * 覆盖正常拆解→分发→汇总、Worker 超时、Worker 失败、拆解为空降级。
 */
class SupervisedOrchestratorTest {

    private InMemoryMessageBus messageBus;
    private CollaborationMetrics collabMetrics;
    private AgentMeshMetrics meshMetrics;
    private SupervisorWorkerCollaboration collaboration;
    private SupervisedOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        collabMetrics = new CollaborationMetrics(registry);
        meshMetrics = new AgentMeshMetrics(registry);
        messageBus = new InMemoryMessageBus(256, collabMetrics);
        collaboration = new SupervisorWorkerCollaboration(
                Duration.ofSeconds(5), meshMetrics, collabMetrics);
        orchestrator = new SupervisedOrchestrator(
                collaboration, messageBus, meshMetrics, Duration.ofSeconds(5));
    }

    @AfterEach
    void tearDown() {
        messageBus.close();
    }

    @Test
    void shouldExecuteSupervisedWorkflow() {
        // 创建 Supervisor Agent
        AgentConfig supervisor = AgentConfig.builder()
                .agentId("supervisor")
                .role("supervisor")
                .delegateTo(List.of("designer", "crafter"))
                .messageBus(messageBus)
                .build();

        // 创建 Worker Agents
        AgentConfig designer = AgentConfig.builder()
                .agentId("designer")
                .role("worker")
                .messageBus(messageBus)
                .build();
        AgentConfig crafter = AgentConfig.builder()
                .agentId("crafter")
                .role("worker")
                .messageBus(messageBus)
                .build();

        // Worker 模拟回复
        setupWorkerReply("designer", "设计灵感：巴洛克风格");
        setupWorkerReply("crafter", "工艺可行性：可制作");

        List<AgentConfig> agents = List.of(supervisor, designer, crafter);
        OrchestrationPlan plan = SimpleOrchestrationPlan.builder()
                .addAgent(supervisor)
                .addAgent(designer)
                .addAgent(crafter)
                .mode(ExecutionMode.SUPERVISED)
                .build();

        Flux<StreamEvent> result = orchestrator.orchestrateStream(plan, "珠宝定制方案");

        StepVerifier.create(result)
                .expectNextMatches(e -> e.getType() == StreamEvent.Type.TEXT
                        && e.getContent().contains("任务执行汇总"))
                .expectNextMatches(e -> e.getType() == StreamEvent.Type.DONE)
                .verifyComplete();
    }

    @Test
    void shouldFallbackWhenNoWorkers() {
        AgentConfig supervisor = AgentConfig.builder()
                .agentId("supervisor")
                .role("supervisor")
                .messageBus(messageBus)
                .build();

        List<AgentConfig> agents = List.of(supervisor);
        OrchestrationPlan plan = SimpleOrchestrationPlan.builder()
                .addAgent(supervisor)
                .mode(ExecutionMode.SUPERVISED)
                .build();

        Flux<StreamEvent> result = orchestrator.orchestrateStream(plan, "任务");

        StepVerifier.create(result)
                .expectNextMatches(e -> e.getType() == StreamEvent.Type.ERROR
                        && e.getContent().contains("Worker"))
                .verifyComplete();
    }

    @Test
    void shouldFallbackWhenNoSupervisor() {
        AgentConfig worker = AgentConfig.builder()
                .agentId("worker-1")
                .role("worker")
                .messageBus(messageBus)
                .build();

        List<AgentConfig> agents = List.of(worker);
        OrchestrationPlan plan = SimpleOrchestrationPlan.builder()
                .addAgent(worker)
                .mode(ExecutionMode.SUPERVISED)
                .build();

        Flux<StreamEvent> result = orchestrator.orchestrateStream(plan, "任务");

        StepVerifier.create(result)
                .expectNextMatches(e -> e.getType() == StreamEvent.Type.ERROR
                        && e.getContent().contains("Supervisor"))
                .verifyComplete();
    }

    @Test
    void shouldHandleWorkerTimeout() {
        AgentConfig supervisor = AgentConfig.builder()
                .agentId("supervisor")
                .role("supervisor")
                .delegateTo(List.of("slow-worker"))
                .messageBus(messageBus)
                .build();

        AgentConfig slowWorker = AgentConfig.builder()
                .agentId("slow-worker")
                .role("worker")
                .messageBus(messageBus)
                .build();

        // slow-worker 不回复，模拟超时
        List<AgentConfig> agents = List.of(supervisor, slowWorker);
        OrchestrationPlan plan = SimpleOrchestrationPlan.builder()
                .addAgent(supervisor)
                .addAgent(slowWorker)
                .mode(ExecutionMode.SUPERVISED)
                .build();

        Flux<StreamEvent> result = orchestrator.orchestrateStream(plan, "任务");

        // 应汇总结果，标记超时
        StepVerifier.create(result)
                .expectNextMatches(e -> e.getType() == StreamEvent.Type.TEXT
                        && e.getContent().contains("失败"))
                .expectNextMatches(e -> e.getType() == StreamEvent.Type.DONE)
                .verifyComplete();
    }

    @Test
    void shouldHandleEmptyPlan() {
        List<AgentConfig> agents = List.of();
        OrchestrationPlan plan = SimpleOrchestrationPlan.builder()
                .mode(ExecutionMode.SUPERVISED)
                .build();

        Flux<StreamEvent> result = orchestrator.orchestrateStream(plan, "任务");

        StepVerifier.create(result)
                .expectNextMatches(e -> e.getType() == StreamEvent.Type.ERROR)
                .verifyComplete();
    }

    // ========== 辅助方法 ==========

    private void setupWorkerReply(String workerId, String replyContent) {
        messageBus.registerAgentRole(workerId, "worker");
        messageBus.subscribe(workerId, msg -> {
            if (msg.getType() == AgentMessage.MessageType.TASK_ASSIGNMENT) {
                AgentMessage reply = AgentMessage.builder()
                        .fromAgentId(workerId)
                        .toAgentId(msg.getFromAgentId())
                        .type(AgentMessage.MessageType.TASK_COMPLETE)
                        .content(replyContent)
                        .replyToMessageId(msg.getMessageId())
                        .build();
                messageBus.send(reply).subscribe();
            }
            return false;
        }).subscribe();
    }
}