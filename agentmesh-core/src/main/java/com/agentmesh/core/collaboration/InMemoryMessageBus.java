package com.agentmesh.core.collaboration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * 内存消息总线实现。
 * 使用 Reactor Sinks.Many 实现热流订阅。
 *
 * 背压控制：缓冲区大小 256，满时拒绝发射（FAIL_FAST），记录告警日志和丢弃指标。
 * 可通过 agentmesh.collaboration.message-buffer-size 配置。
 *
 * 生命周期：实现 DisposableBean，Spring 容器销毁时自动调用 close() 清理所有 Sink。
 */
@Slf4j
public class InMemoryMessageBus implements MessageBus, DisposableBean {

    private static final int DEFAULT_BUFFER_SIZE = 256;

    /** 每个 Agent 对应一个 Sink，用于推送消息 */
    private final Map<String, Sinks.Many<AgentMessage>> agentSinks = new ConcurrentHashMap<>();

    /** Agent ID → Role 映射表，用于 broadcast 按 role 过滤 */
    private final Map<String, String> agentRoles = new ConcurrentHashMap<>();

    private final int bufferSize;
    private final CollaborationMetrics metrics;

    public InMemoryMessageBus(int bufferSize, CollaborationMetrics metrics) {
        this.bufferSize = bufferSize > 0 ? bufferSize : DEFAULT_BUFFER_SIZE;
        this.metrics = metrics;
    }

    @Override
    public Mono<String> send(AgentMessage message) {
        return Mono.fromCallable(() -> {
            String msgId = message.getMessageId() != null
                    ? message.getMessageId() : UUID.randomUUID().toString().substring(0, 8);
            if (message.getMessageId() == null) {
                message.setMessageId(msgId);
            }
            // 自动补全 timestamp（调用方未设置时）
            if (message.getTimestamp() == null) {
                message.setTimestamp(Instant.now());
            }
            if (message.getToAgentId() != null) {
                Sinks.Many<AgentMessage> sink = agentSinks.get(message.getToAgentId());
                if (sink != null) {
                    Sinks.EmitResult emitResult = sink.tryEmitNext(message);
                    if (emitResult.isFailure()) {
                        log.warn("[MessageBus] 消息发送失败(背压): {} -> {} type={}, result={}",
                                message.getFromAgentId(), message.getToAgentId(),
                                message.getType(), emitResult);
                        metrics.recordMessageDropped();
                    } else {
                        metrics.recordMessageSent(message.getType());
                        log.debug("[MessageBus] 消息发送: {} -> {} type={}",
                                message.getFromAgentId(), message.getToAgentId(), message.getType());
                    }
                } else {
                    log.warn("[MessageBus] 目标 Agent 未订阅: {}", message.getToAgentId());
                }
            }
            return msgId;
        });
    }

    @Override
    public Mono<String> broadcast(AgentMessage message, String targetRole) {
        return Mono.fromCallable(() -> {
            String msgId = message.getMessageId() != null
                    ? message.getMessageId() : UUID.randomUUID().toString().substring(0, 8);
            if (message.getMessageId() == null) {
                message.setMessageId(msgId);
            }
            // 自动补全 timestamp（调用方未设置时）
            if (message.getTimestamp() == null) {
                message.setTimestamp(Instant.now());
            }
            int count = 0;
            int dropped = 0;
            for (var entry : agentSinks.entrySet()) {
                // 排除发送方
                if (entry.getKey().equals(message.getFromAgentId())) {
                    continue;
                }
                // 按 targetRole 过滤（targetRole 为 null 时广播给所有 Agent）
                if (targetRole != null) {
                    String agentRole = agentRoles.get(entry.getKey());
                    if (!targetRole.equals(agentRole)) {
                        continue;
                    }
                }
                Sinks.EmitResult result = entry.getValue().tryEmitNext(message);
                if (result.isFailure()) {
                    log.warn("[MessageBus] 广播消息发送失败(背压): to={} type={} result={}",
                            entry.getKey(), message.getType(), result);
                    metrics.recordMessageDropped();
                    dropped++;
                } else {
                    count++;
                }
            }
            log.debug("[MessageBus] 广播: from={} type={}, targetRole={}, 成功={}, 丢弃={}",
                    message.getFromAgentId(), message.getType(), targetRole, count, dropped);
            metrics.recordMessageBroadcast(message.getType(), count);
            return msgId;
        });
    }

    /**
     * 注册 Agent 角色。
     * 必须在 subscribe() 之前调用，否则 broadcast 按 role 过滤将无效。
     * @param agentId Agent ID
     * @param role    Agent 角色（supervisor/worker/debater 等）
     */
    @Override
    public void registerAgentRole(String agentId, String role) {
        agentRoles.put(agentId, role);
        log.info("[MessageBus] 注册 Agent 角色: agentId={} role={}", agentId, role);
    }

    @Override
    public Flux<AgentMessage> subscribe(String agentId) {
        return subscribe(agentId, msg -> true);
    }

    @Override
    public Flux<AgentMessage> subscribe(String agentId, Predicate<AgentMessage> filter) {
        Sinks.Many<AgentMessage> sink = agentSinks.computeIfAbsent(agentId,
                k -> Sinks.many().multicast().onBackpressureBuffer(bufferSize));
        String role = agentRoles.get(agentId);
        if (role == null) {
            log.warn("[MessageBus] Agent {} 未注册 role，将无法接收定向广播（broadcast 按 targetRole 过滤时会被跳过）",
                    agentId);
        }
        log.info("[MessageBus] Agent {} (role={}) 订阅消息总线, bufferSize={}", agentId, role, bufferSize);
        return sink.asFlux()
                .filter(filter)
                .doOnNext(msg -> log.debug("[MessageBus] Agent {} 收到消息: type={} from={} messageId={}",
                        agentId, msg.getType(), msg.getFromAgentId(), msg.getMessageId()));
    }

    /**
     * 取消订阅，清理对应 Sink，防止长期运行内存泄漏。
     * 协作流程结束后由编排器调用。
     */
    @Override
    public void unsubscribe(String agentId) {
        Sinks.Many<AgentMessage> removed = agentSinks.remove(agentId);
        if (removed != null) {
            removed.tryEmitComplete();
        }
        agentRoles.remove(agentId);
        log.info("[MessageBus] Agent {} 取消订阅，已清理 Sink 和角色映射", agentId);
    }

    @Override
    public Mono<AgentMessage> requestReply(AgentMessage message, Duration timeout) {
        long start = System.currentTimeMillis();
        // 提前生成 messageId，用于过滤回复
        String msgId = message.getMessageId() != null
                ? message.getMessageId() : UUID.randomUUID().toString().substring(0, 8);
        if (message.getMessageId() == null) {
            message.setMessageId(msgId);
        }
        // 自动补全 timestamp
        if (message.getTimestamp() == null) {
            message.setTimestamp(Instant.now());
        }

        final String finalMsgId = msgId;
        final String senderId = message.getFromAgentId();

        // 确保 sender 的 Sink 已创建
        Sinks.Many<AgentMessage> senderSink = agentSinks.computeIfAbsent(senderId,
                k -> Sinks.many().multicast().onBackpressureBuffer(bufferSize));

        return Mono.<AgentMessage>create(sink -> {
            // 先订阅回复通道（建立订阅后再发送消息，避免回复在订阅前到达）
            Disposable subscription = senderSink.asFlux()
                    .filter(reply -> finalMsgId.equals(reply.getReplyToMessageId()))
                    .next()
                    .timeout(timeout)
                    .subscribe(
                            reply -> sink.success(reply),
                            error -> sink.error(error)
                    );

            // 订阅建立后立即发送消息
            send(message).subscribe(
                    null,
                    err -> {
                        log.error("[MessageBus] requestReply 发送消息失败: messageId={} from={} to={}",
                                finalMsgId, senderId, message.getToAgentId(), err);
                        sink.error(err);
                    }
            );

            // 取消时清理订阅
            sink.onCancel(subscription);
        })
        .doOnSuccess(reply -> metrics.recordRequestReply(message.getType(),
                System.currentTimeMillis() - start))
        .doOnError(e -> {
            log.warn("[MessageBus] requestReply 超时: messageId={} from={} to={} timeout={}s",
                    finalMsgId, senderId, message.getToAgentId(),
                    timeout.toSeconds());
            metrics.recordRequestReplyTimeout(message.getType());
        });
    }

    /**
     * 关闭消息总线，清理所有 Agent 的 Sink 和角色映射。
     * Spring 容器销毁时自动调用（实现 DisposableBean）。
     */
    @Override
    public void destroy() {
        close();
    }

    public void close() {
        int count = agentSinks.size();
        agentSinks.forEach((id, sink) -> sink.tryEmitComplete());
        agentSinks.clear();
        agentRoles.clear();
        log.info("[MessageBus] 消息总线已关闭，清理了 {} 个 Agent 的 Sink", count);
    }
}
