package com.agentmesh.core.collaboration;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MessageBus 集成测试。
 * 覆盖多 Agent 间消息收发、广播场景、traceId 全链路传递。
 */
class MessageBusIntegrationTest {

    private InMemoryMessageBus messageBus;
    private CollaborationMetrics metrics;

    @BeforeEach
    void setUp() {
        metrics = new CollaborationMetrics(new SimpleMeterRegistry());
        messageBus = new InMemoryMessageBus(256, metrics);
    }

    @AfterEach
    void tearDown() {
        messageBus.close();
    }

    @Test
    void shouldSupportMultiAgentMessaging() {
        // 注册 3 个 Agent 角色
        messageBus.registerAgentRole("supervisor", "supervisor");
        messageBus.registerAgentRole("worker-1", "worker");
        messageBus.registerAgentRole("worker-2", "worker");

        // 订阅
        messageBus.subscribe("supervisor");
        messageBus.subscribe("worker-1");
        messageBus.subscribe("worker-2");

        AtomicReference<String> worker1Received = new AtomicReference<>();
        AtomicReference<String> worker2Received = new AtomicReference<>();

        // Worker 处理消息
        messageBus.subscribe("worker-1", msg -> {
            if (msg.getType() == AgentMessage.MessageType.TASK_ASSIGNMENT) {
                worker1Received.set(msg.getContent());
                // 回复
                AgentMessage reply = AgentMessage.builder()
                        .fromAgentId("worker-1")
                        .toAgentId("supervisor")
                        .type(AgentMessage.MessageType.TASK_COMPLETE)
                        .content("worker-1 done")
                        .replyToMessageId(msg.getMessageId())
                        .build();
                messageBus.send(reply).subscribe();
            }
            return false;
        }).subscribe();

        messageBus.subscribe("worker-2", msg -> {
            if (msg.getType() == AgentMessage.MessageType.TASK_ASSIGNMENT) {
                worker2Received.set(msg.getContent());
            }
            return false;
        }).subscribe();

        // Supervisor 发送任务
        AgentMessage task1 = AgentMessage.builder()
                .fromAgentId("supervisor")
                .toAgentId("worker-1")
                .type(AgentMessage.MessageType.TASK_ASSIGNMENT)
                .content("task for worker-1")
                .build();

        AgentMessage task2 = AgentMessage.builder()
                .fromAgentId("supervisor")
                .toAgentId("worker-2")
                .type(AgentMessage.MessageType.TASK_ASSIGNMENT)
                .content("task for worker-2")
                .build();

        messageBus.send(task1).block();
        messageBus.send(task2).block();

        // 等待异步处理
        try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        assertEquals("task for worker-1", worker1Received.get());
        assertEquals("task for worker-2", worker2Received.get());
    }

    @Test
    void shouldBroadcastContextUpdate() {
        messageBus.registerAgentRole("worker-1", "worker");
        messageBus.registerAgentRole("worker-2", "worker");
        messageBus.registerAgentRole("worker-3", "worker");
        messageBus.subscribe("worker-1");
        messageBus.subscribe("worker-2");
        messageBus.subscribe("worker-3");

        AtomicReference<String> w1 = new AtomicReference<>();
        AtomicReference<String> w2 = new AtomicReference<>();
        AtomicReference<String> w3 = new AtomicReference<>();

        messageBus.subscribe("worker-1", msg -> { w1.set(msg.getContent()); return false; }).subscribe();
        messageBus.subscribe("worker-2", msg -> { w2.set(msg.getContent()); return false; }).subscribe();
        messageBus.subscribe("worker-3", msg -> { w3.set(msg.getContent()); return false; }).subscribe();

        AgentMessage broadcast = AgentMessage.builder()
                .fromAgentId("supervisor")
                .type(AgentMessage.MessageType.CONTEXT_UPDATE)
                .content("context changed")
                .build();

        messageBus.broadcast(broadcast, "worker").block();

        try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        assertEquals("context changed", w1.get());
        assertEquals("context changed", w2.get());
        assertEquals("context changed", w3.get());
    }

    @Test
    void shouldPassTraceIdAcrossMessages() {
        String traceId = UUID.randomUUID().toString().substring(0, 8);
        String collaborationId = "collab-" + UUID.randomUUID().toString().substring(0, 4);

        messageBus.registerAgentRole("worker-1", "worker");
        messageBus.subscribe("worker-1");

        AtomicReference<String> receivedTraceId = new AtomicReference<>();
        AtomicReference<String> receivedCollabId = new AtomicReference<>();

        messageBus.subscribe("worker-1", msg -> {
            receivedTraceId.set(msg.getTraceId());
            receivedCollabId.set(msg.getCollaborationId());
            return false;
        }).subscribe();

        AgentMessage msg = AgentMessage.builder()
                .fromAgentId("supervisor")
                .toAgentId("worker-1")
                .type(AgentMessage.MessageType.QUERY)
                .content("test")
                .traceId(traceId)
                .collaborationId(collaborationId)
                .build();

        messageBus.send(msg).block();

        try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        assertEquals(traceId, receivedTraceId.get());
        assertEquals(collaborationId, receivedCollabId.get());
    }

    @Test
    void shouldIsolateSubscriptions() {
        messageBus.registerAgentRole("agent-a", "worker");
        messageBus.registerAgentRole("agent-b", "worker");
        messageBus.subscribe("agent-a");
        messageBus.subscribe("agent-b");

        // 取消 agent-b
        messageBus.unsubscribe("agent-b");

        AtomicReference<String> aReceived = new AtomicReference<>();
        messageBus.subscribe("agent-a", msg -> { aReceived.set(msg.getContent()); return false; }).subscribe();

        AgentMessage msg = AgentMessage.builder()
                .fromAgentId("supervisor")
                .toAgentId("agent-a")
                .type(AgentMessage.MessageType.QUERY)
                .content("only for a")
                .build();
        messageBus.send(msg).block();

        try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        assertEquals("only for a", aReceived.get());
    }

    @Test
    void shouldHandleRequestReplyWithReplyToMessageId() {
        messageBus.registerAgentRole("supervisor", "supervisor");
        messageBus.registerAgentRole("worker-1", "worker");
        messageBus.subscribe("supervisor");
        messageBus.subscribe("worker-1");

        // Worker 监听并回复
        messageBus.subscribe("worker-1", msg -> {
            if (msg.getType() == AgentMessage.MessageType.QUERY) {
                AgentMessage reply = AgentMessage.builder()
                        .fromAgentId("worker-1")
                        .toAgentId("supervisor")
                        .type(AgentMessage.MessageType.RESPONSE)
                        .content("answer")
                        .replyToMessageId(msg.getMessageId())
                        .build();
                messageBus.send(reply).subscribe();
            }
            return false;
        }).subscribe();

        AgentMessage request = AgentMessage.builder()
                .fromAgentId("supervisor")
                .toAgentId("worker-1")
                .type(AgentMessage.MessageType.QUERY)
                .content("question")
                .build();

        AgentMessage reply = messageBus.requestReply(request, Duration.ofSeconds(5)).block();

        assertNotNull(reply);
        assertEquals("answer", reply.getContent());
        assertEquals(request.getMessageId(), reply.getReplyToMessageId());
    }
}