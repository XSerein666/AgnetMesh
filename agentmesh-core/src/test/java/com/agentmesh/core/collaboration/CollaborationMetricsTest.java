package com.agentmesh.core.collaboration;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CollaborationMetrics 测试。
 * 验证所有指标注册和记录。
 */
class CollaborationMetricsTest {

    private MeterRegistry registry;
    private CollaborationMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new CollaborationMetrics(registry);
    }

    @Test
    void shouldRecordMessageSent() {
        metrics.recordMessageSent(AgentMessage.MessageType.QUERY);
        double count = registry.get("agentmesh.collaboration.messages.sent")
                .tag("type", "QUERY").counter().count();
        assertEquals(1.0, count);
    }

    @Test
    void shouldRecordMessageDropped() {
        metrics.recordMessageDropped();
        double count = registry.get("agentmesh.collaboration.messages.dropped").counter().count();
        assertEquals(1.0, count);
    }

    @Test
    void shouldRecordMessageBroadcast() {
        metrics.recordMessageBroadcast(AgentMessage.MessageType.CONTEXT_UPDATE, 3);
        double count = registry.get("agentmesh.collaboration.messages.broadcast")
                .tag("type", "CONTEXT_UPDATE").counter().count();
        assertEquals(3.0, count);
    }

    @Test
    void shouldRecordRequestReply() {
        metrics.recordRequestReply(AgentMessage.MessageType.QUERY, 1500);
        double totalTime = registry.get("agentmesh.collaboration.request_reply.duration")
                .tag("type", "QUERY").timer().totalTime(
                        java.util.concurrent.TimeUnit.MILLISECONDS);
        assertTrue(totalTime > 0);
    }

    @Test
    void shouldRecordRequestReplyTimeout() {
        metrics.recordRequestReplyTimeout(AgentMessage.MessageType.QUERY);
        double count = registry.get("agentmesh.collaboration.request_reply.timeout").counter().count();
        assertEquals(1.0, count);
    }

    @Test
    void shouldRecordWorkerResult() {
        metrics.recordWorkerResult("designer", true);
        metrics.recordWorkerResult("crafter", false);

        double success = registry.get("agentmesh.collaboration.worker.success")
                .tag("worker", "designer").counter().count();
        double failure = registry.get("agentmesh.collaboration.worker.failure")
                .tag("worker", "crafter").counter().count();

        assertEquals(1.0, success);
        assertEquals(1.0, failure);
    }

    @Test
    void shouldRecordCollaborationLifecycle() {
        metrics.recordCollaborationStarted();
        metrics.recordCollaborationCompleted();
        metrics.recordCollaborationFailed();

        assertEquals(1.0, registry.get("agentmesh.collaboration.started").counter().count());
        assertEquals(1.0, registry.get("agentmesh.collaboration.completed").counter().count());
        assertEquals(1.0, registry.get("agentmesh.collaboration.failed").counter().count());
    }
}