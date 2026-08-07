package com.agentmesh.core.collaboration;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 协作指标。
 * 覆盖消息、Worker 响应、协作成功率。
 *
 * 线程安全：
 * - 动态 Counter/Timer 使用 ConcurrentHashMap 存储，computeIfAbsent 保证并发安全
 * - 固定 Counter 使用 Micrometer Counter（内部基于 AtomicLong，线程安全）
 * - 所有 record* 方法可在多线程下安全调用，无需额外同步
 */
public class CollaborationMetrics {

    private final MeterRegistry registry;

    // 消息指标
    private final ConcurrentHashMap<String, Counter> messageSentCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Counter> messageBroadcastCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Timer> requestReplyTimers = new ConcurrentHashMap<>();
    private final Counter messageDroppedCounter;
    private final Counter requestReplyTimeoutCounter;

    // Worker 指标
    private final ConcurrentHashMap<String, Counter> workerSuccessCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Counter> workerFailureCounters = new ConcurrentHashMap<>();

    // 协作流程指标
    private final Counter collaborationStartedCounter;
    private final Counter collaborationCompletedCounter;
    private final Counter collaborationFailedCounter;

    public CollaborationMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.messageDroppedCounter = Counter.builder("agentmesh.collaboration.messages.dropped")
                .description("背压丢弃的消息数").register(registry);
        this.requestReplyTimeoutCounter = Counter.builder("agentmesh.collaboration.request_reply.timeout")
                .description("requestReply 超时次数").register(registry);
        this.collaborationStartedCounter = Counter.builder("agentmesh.collaboration.started")
                .description("协作流程启动次数").register(registry);
        this.collaborationCompletedCounter = Counter.builder("agentmesh.collaboration.completed")
                .description("协作流程成功完成次数").register(registry);
        this.collaborationFailedCounter = Counter.builder("agentmesh.collaboration.failed")
                .description("协作流程失败次数").register(registry);
    }

    public void recordMessageSent(AgentMessage.MessageType type) {
        messageSentCounters.computeIfAbsent(type.name(),
                t -> Counter.builder("agentmesh.collaboration.messages.sent")
                        .tag("type", t).register(registry)).increment();
    }

    public void recordMessageDropped() {
        messageDroppedCounter.increment();
    }

    public void recordRequestReplyTimeout(AgentMessage.MessageType type) {
        requestReplyTimeoutCounter.increment();
    }

    public void recordMessageBroadcast(AgentMessage.MessageType type, int count) {
        Counter counter = messageBroadcastCounters.computeIfAbsent(type.name(),
                t -> Counter.builder("agentmesh.collaboration.messages.broadcast")
                        .tag("type", t)
                        .description("广播消息接收方数量")
                        .register(registry));
        counter.increment(count);
    }

    public void recordRequestReply(AgentMessage.MessageType type, long durationMs) {
        Timer timer = requestReplyTimers.computeIfAbsent(type.name(),
                t -> Timer.builder("agentmesh.collaboration.request_reply.duration")
                        .tag("type", t)
                        .description("request-reply 耗时")
                        .register(registry));
        timer.record(durationMs, TimeUnit.MILLISECONDS);
    }

    public void recordWorkerResult(String workerId, boolean success) {
        if (success) {
            workerSuccessCounters.computeIfAbsent(workerId,
                    w -> Counter.builder("agentmesh.collaboration.worker.success")
                            .tag("worker", w).register(registry)).increment();
        } else {
            workerFailureCounters.computeIfAbsent(workerId,
                    w -> Counter.builder("agentmesh.collaboration.worker.failure")
                            .tag("worker", w).register(registry)).increment();
        }
    }

    public void recordCollaborationStarted() {
        collaborationStartedCounter.increment();
    }

    public void recordCollaborationCompleted() {
        collaborationCompletedCounter.increment();
    }

    public void recordCollaborationFailed() {
        collaborationFailedCounter.increment();
    }
}
