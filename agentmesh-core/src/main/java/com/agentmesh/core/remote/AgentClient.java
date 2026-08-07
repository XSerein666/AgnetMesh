package com.agentmesh.core.remote;

import com.agentmesh.core.llm.StreamEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * 远程 Agent 调用客户端。
 */
public interface AgentClient {

    /**
     * 同步调用远程 Agent 的指定技能
     * @param agentUrl  目标 Agent 的 HTTP 端点
     * @param skillId   技能 ID
     * @param input     技能输入参数
     * @return 执行结果（成功时为技能输出，失败时为包含 error 字段的 Map）
     */
    Object callSkill(String agentUrl, String skillId, Map<String, Object> input);

    /**
     * 异步调用远程 Agent 的指定技能。
     * 非阻塞轮询，不占用调用线程。
     * @param agentUrl  目标 Agent 的 HTTP 端点
     * @param skillId   技能 ID
     * @param input     技能输入参数
     * @param agentId   远程 Agent 标识（用于指标 tag，避免 url 分裂序列）
     * @return Mono 包装的执行结果
     */
    Mono<Object> callSkillAsync(String agentUrl, String skillId, Map<String, Object> input, String agentId);

    /**
     * 流式调用远程 Agent 的聊天端点（Phase 8 新增）。
     * 直连远程 /chat/stream，逐事件解析并透传。
     * 默认超时由 RemoteToolProperties.streamTimeout 控制。
     * @param agentUrl 目标 Agent 的 HTTP 端点
     * @param message  用户消息
     * @param agentId  远程 Agent 标识（用于指标 tag）
     * @return SSE 事件流
     */
    Flux<StreamEvent> callSkillStream(String agentUrl, String message, String agentId);

    /**
     * 流式调用远程 Agent（携带 traceId，用于跨服务链路关联）。
     * traceId 非空时才设置 X-Trace-Id 头，防止 "null" 字符串污染下游。
     * 此重载用于并行编排等需要显式传递 traceId 的场景（reactor 线程中不能读 ThreadLocal）。
     * @param agentUrl 目标 Agent 的 HTTP 端点
     * @param message  用户消息
     * @param traceId  链路追踪 ID，可为 null
     * @param agentId  远程 Agent 标识（用于指标 tag）
     * @return SSE 事件流
     */
    Flux<StreamEvent> callSkillStream(String agentUrl, String message, String traceId, String agentId);
}
