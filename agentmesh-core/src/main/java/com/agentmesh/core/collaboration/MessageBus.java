package com.agentmesh.core.collaboration;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.time.Duration;
import java.util.function.Predicate;

/**
 * Agent 间消息总线。
 *
 * 超时机制：
 * - requestReply 默认超时 30s（可通过 agentmesh.collaboration.request-timeout 配置）
 * - 超时后抛出 TimeoutException，由调用方（Supervisor）降级处理
 * - 降级策略：标记该 Worker 为失败，不阻塞其他 Worker
 *
 * 背压控制：
 * - InMemoryMessageBus 内置背压缓冲区（默认 256 条消息）
 * - 缓冲区满时拒绝发射（FAIL_FAST），记录告警日志和丢弃指标
 */
public interface MessageBus {

    /** 默认 request-reply 超时时间 */
    Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(30);

    /**
     * 发送消息到指定 Agent。
     */
    Mono<String> send(AgentMessage message);

    /**
     * 广播消息到指定 role 的所有 Agent（除发送方）。
     * @param message 消息体
     * @param targetRole 目标角色（如 "worker"），null 表示所有 Agent
     */
    Mono<String> broadcast(AgentMessage message, String targetRole);

    /**
     * 订阅发给本 Agent 的消息流。
     */
    Flux<AgentMessage> subscribe(String agentId);

    /**
     * 订阅符合过滤条件的消息流。
     */
    Flux<AgentMessage> subscribe(String agentId, Predicate<AgentMessage> filter);

    /**
     * 发送消息并等待回复。
     *
     * @param message 请求消息
     * @param timeout 超时时间（默认 30s）
     * @return 回复消息；超时时 Mono 以 TimeoutException 错误终止
     */
    Mono<AgentMessage> requestReply(AgentMessage message, Duration timeout);

    /**
     * 发送消息并等待回复（使用默认超时 30s）。
     */
    default Mono<AgentMessage> requestReply(AgentMessage message) {
        return requestReply(message, DEFAULT_REQUEST_TIMEOUT);
    }

    /**
     * 注册 Agent 角色，用于 broadcast 按 targetRole 过滤。
     * 必须在 subscribe() 之前调用，否则定向广播无法送达该 Agent。
     * @param agentId Agent ID
     * @param role    Agent 角色（supervisor/worker/debater 等）
     */
    void registerAgentRole(String agentId, String role);

    /**
     * 取消订阅，清理 Agent 的消息通道和角色映射。
     * 协作流程结束后由编排器调用，防止内存泄漏。
     * @param agentId Agent ID
     */
    void unsubscribe(String agentId);
}
