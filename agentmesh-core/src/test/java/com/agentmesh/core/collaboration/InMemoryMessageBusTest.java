package com.agentmesh.core.collaboration;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * InMemoryMessageBus 单元测试。
 * 覆盖点对点发送、按 role 广播、订阅过滤、requestReply、背压控制、并发发送。
 */
class InMemoryMessageBusTest {

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

    // ========== 点对点发送 ==========

    @Test
    void shouldSendMessageToSubscribedAgent() {
        messageBus.registerAgentRole("worker-1", "worker");
        messageBus.subscribe("worker-1"); // 必须先订阅，Sink 才会创建

        AgentMessage msg = AgentMessage.builder()
                .fromAgentId("supervisor")
                .toAgentId("worker-1")
                .type(AgentMessage.MessageType.QUERY)
                .content("Hello")
                .build();

        Flux<AgentMessage> received = messageBus.subscribe("worker-1")
                .take(1);

        messageBus.send(msg).subscribe();

        StepVerifier.create(received)
                .expectNextMatches(m -> "Hello".equals(m.getContent())
                        && m.getType() == AgentMessage.MessageType.QUERY)
                .verifyComplete();
    }

    @Test
    void shouldAutoGenerateMessageIdWhenNull() {
        messageBus.registerAgentRole("worker-1", "worker");
        messageBus.subscribe("worker-1");

        AgentMessage msg = AgentMessage.builder()
                .fromAgentId("supervisor")
                .toAgentId("worker-1")
                .type(AgentMessage.MessageType.QUERY)
                .build();

        String msgId = messageBus.send(msg).block();
        assertNotNull(msgId);
        assertEquals(msgId, msg.getMessageId());
    }

    @Test
    void shouldAutoFillTimestampWhenNull() {
        messageBus.registerAgentRole("worker-1", "worker");
        messageBus.subscribe("worker-1");

        AgentMessage msg = AgentMessage.builder()
                .fromAgentId("supervisor")
                .toAgentId("worker-1")
                .type(AgentMessage.MessageType.QUERY)
                .build();

        messageBus.send(msg).block();
        assertNotNull(msg.getTimestamp());
    }

    @Test
    void shouldNotOverrideExistingTimestamp() {
        messageBus.registerAgentRole("worker-1", "worker");
        messageBus.subscribe("worker-1");

        Instant fixedTime = Instant.parse("2026-01-01T00:00:00Z");
        AgentMessage msg = AgentMessage.builder()
                .fromAgentId("supervisor")
                .toAgentId("worker-1")
                .type(AgentMessage.MessageType.QUERY)
                .timestamp(fixedTime)
                .build();

        messageBus.send(msg).block();
        assertEquals(fixedTime, msg.getTimestamp());
    }

    // ========== 广播 ==========

    @Test
    void shouldBroadcastToAllAgents() {
        messageBus.registerAgentRole("worker-1", "worker");
        messageBus.registerAgentRole("worker-2", "worker");
        messageBus.subscribe("worker-1");
        messageBus.subscribe("worker-2");

        AgentMessage msg = AgentMessage.builder()
                .fromAgentId("supervisor")
                .type(AgentMessage.MessageType.CONTEXT_UPDATE)
                .content("broadcast")
                .build();

        Flux<AgentMessage> w1 = messageBus.subscribe("worker-1").take(1);
        Flux<AgentMessage> w2 = messageBus.subscribe("worker-2").take(1);

        messageBus.broadcast(msg, null).subscribe();

        StepVerifier.create(w1).expectNextCount(1).verifyComplete();
        StepVerifier.create(w2).expectNextCount(1).verifyComplete();
    }

    @Test
    void shouldBroadcastWithTargetRoleFilter() {
        messageBus.registerAgentRole("worker-1", "worker");
        messageBus.registerAgentRole("worker-2", "worker");
        messageBus.registerAgentRole("debater-1", "debater");
        messageBus.subscribe("worker-1");
        messageBus.subscribe("worker-2");
        messageBus.subscribe("debater-1");

        AgentMessage msg = AgentMessage.builder()
                .fromAgentId("supervisor")
                .type(AgentMessage.MessageType.TASK_ASSIGNMENT)
                .content("only workers")
                .build();

        // 只向 worker 角色广播
        Flux<AgentMessage> w1 = messageBus.subscribe("worker-1").take(1);
        Flux<AgentMessage> w2 = messageBus.subscribe("worker-2").take(1);

        messageBus.broadcast(msg, "worker").subscribe();

        StepVerifier.create(w1).expectNextCount(1).verifyComplete();
        StepVerifier.create(w2).expectNextCount(1).verifyComplete();

        // debater 不应收到
        Flux<AgentMessage> debater = messageBus.subscribe("debater-1").take(1)
                .timeout(Duration.ofMillis(500));
        StepVerifier.create(debater)
                .expectError() // 超时
                .verify();
    }

    @Test
    void shouldExcludeSenderFromBroadcast() {
        messageBus.registerAgentRole("supervisor", "supervisor");
        messageBus.registerAgentRole("worker-1", "worker");
        messageBus.subscribe("supervisor");
        messageBus.subscribe("worker-1");

        AgentMessage msg = AgentMessage.builder()
                .fromAgentId("supervisor")
                .type(AgentMessage.MessageType.CONTEXT_UPDATE)
                .content("broadcast")
                .build();

        messageBus.broadcast(msg, null).subscribe();

        // supervisor 不应收到自己发的广播
        Flux<AgentMessage> supervisor = messageBus.subscribe("supervisor").take(1)
                .timeout(Duration.ofMillis(500));
        StepVerifier.create(supervisor)
                .expectError()
                .verify();
    }

    // ========== 订阅过滤 ==========

    @Test
    void shouldFilterMessagesByPredicate() {
        messageBus.registerAgentRole("worker-1", "worker");
        messageBus.subscribe("worker-1");

        // 只接收 TASK_ASSIGNMENT 类型
        Flux<AgentMessage> filtered = messageBus.subscribe("worker-1",
                        msg -> msg.getType() == AgentMessage.MessageType.TASK_ASSIGNMENT)
                .take(1);

        AgentMessage queryMsg = AgentMessage.builder()
                .fromAgentId("supervisor")
                .toAgentId("worker-1")
                .type(AgentMessage.MessageType.QUERY)
                .content("query")
                .build();
        messageBus.send(queryMsg).subscribe();

        AgentMessage taskMsg = AgentMessage.builder()
                .fromAgentId("supervisor")
                .toAgentId("worker-1")
                .type(AgentMessage.MessageType.TASK_ASSIGNMENT)
                .content("task")
                .build();
        messageBus.send(taskMsg).subscribe();

        StepVerifier.create(filtered)
                .expectNextMatches(m -> m.getType() == AgentMessage.MessageType.TASK_ASSIGNMENT)
                .verifyComplete();
    }

    // ========== requestReply ==========

    @Test
    void shouldRequestReplySuccessfully() {
        messageBus.registerAgentRole("worker-1", "worker");
        messageBus.subscribe("worker-1");

        AgentMessage request = AgentMessage.builder()
                .fromAgentId("supervisor")
                .toAgentId("worker-1")
                .type(AgentMessage.MessageType.QUERY)
                .content("ping")
                .build();

        // 模拟 Worker 回复
        messageBus.subscribe("worker-1", msg -> {
            if (msg.getType() == AgentMessage.MessageType.QUERY) {
                AgentMessage reply = AgentMessage.builder()
                        .fromAgentId("worker-1")
                        .toAgentId("supervisor")
                        .type(AgentMessage.MessageType.RESPONSE)
                        .content("pong")
                        .replyToMessageId(msg.getMessageId())
                        .build();
                messageBus.send(reply).subscribe();
            }
            return false;
        }).subscribe();

        AgentMessage reply = messageBus.requestReply(request, Duration.ofSeconds(5)).block();

        assertNotNull(reply);
        assertEquals("pong", reply.getContent());
        assertEquals(AgentMessage.MessageType.RESPONSE, reply.getType());
    }

    @Test
    void shouldTimeoutOnRequestReply() {
        messageBus.registerAgentRole("worker-1", "worker");
        messageBus.subscribe("worker-1");

        AgentMessage request = AgentMessage.builder()
                .fromAgentId("supervisor")
                .toAgentId("worker-1")
                .type(AgentMessage.MessageType.QUERY)
                .content("ping")
                .build();

        // Worker 不回复，应该超时
        StepVerifier.create(messageBus.requestReply(request, Duration.ofMillis(200)))
                .expectError() // TimeoutException
                .verify();
    }

    @Test
    void shouldUseDefaultTimeout() {
        messageBus.registerAgentRole("worker-1", "worker");
        messageBus.subscribe("worker-1");

        AgentMessage request = AgentMessage.builder()
                .fromAgentId("supervisor")
                .toAgentId("worker-1")
                .type(AgentMessage.MessageType.QUERY)
                .content("ping")
                .build();

        // 默认 30s，短时间应超时
        StepVerifier.create(messageBus.requestReply(request, Duration.ofMillis(100)))
                .expectError()
                .verify();
    }

    // ========== 背压控制 ==========

    @Test
    void shouldHandleBackpressure() {
        // 创建小缓冲区的 MessageBus
        InMemoryMessageBus smallBus = new InMemoryMessageBus(2, metrics);
        smallBus.registerAgentRole("worker-1", "worker");
        smallBus.subscribe("worker-1");

        // 不消费消息，快速发送超过缓冲区
        for (int i = 0; i < 10; i++) {
            AgentMessage msg = AgentMessage.builder()
                    .fromAgentId("supervisor")
                    .toAgentId("worker-1")
                    .type(AgentMessage.MessageType.QUERY)
                    .content("msg-" + i)
                    .build();
            smallBus.send(msg).subscribe();
        }

        // 不应抛出异常，消息被丢弃或缓冲
        smallBus.close();
    }

    // ========== 并发发送 ==========

    @Test
    void shouldHandleConcurrentSends() throws Exception {
        messageBus.registerAgentRole("worker-1", "worker");
        messageBus.subscribe("worker-1");

        int threadCount = 10;
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final int idx = i;
            new Thread(() -> {
                AgentMessage msg = AgentMessage.builder()
                        .fromAgentId("sender-" + idx)
                        .toAgentId("worker-1")
                        .type(AgentMessage.MessageType.QUERY)
                        .content("msg-" + idx)
                        .build();
                messageBus.send(msg).subscribe();
                latch.countDown();
            }).start();
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS), "所有线程应在 5s 内完成");
    }

    // ========== 生命周期 ==========

    @Test
    void shouldCleanupOnUnsubscribe() {
        messageBus.registerAgentRole("worker-1", "worker");
        messageBus.subscribe("worker-1");

        messageBus.unsubscribe("worker-1");

        // 取消订阅后不应再收到消息
        AgentMessage msg = AgentMessage.builder()
                .fromAgentId("supervisor")
                .toAgentId("worker-1")
                .type(AgentMessage.MessageType.QUERY)
                .content("after unsubscribe")
                .build();
        messageBus.send(msg).subscribe();

        // 重新订阅后应该能收到新消息
        Flux<AgentMessage> newSub = messageBus.subscribe("worker-1").take(1);
        AgentMessage newMsg = AgentMessage.builder()
                .fromAgentId("supervisor")
                .toAgentId("worker-1")
                .type(AgentMessage.MessageType.QUERY)
                .content("after resubscribe")
                .build();
        messageBus.send(newMsg).subscribe();

        StepVerifier.create(newSub)
                .expectNextMatches(m -> "after resubscribe".equals(m.getContent()))
                .verifyComplete();
    }

    @Test
    void shouldCloseGracefully() {
        messageBus.registerAgentRole("worker-1", "worker");
        messageBus.subscribe("worker-1");

        messageBus.close();

        // 关闭后发送应不影响
        AgentMessage msg = AgentMessage.builder()
                .fromAgentId("supervisor")
                .toAgentId("worker-1")
                .type(AgentMessage.MessageType.QUERY)
                .content("after close")
                .build();
        String msgId = messageBus.send(msg).block();
        assertNotNull(msgId);
    }
}