package com.agentmesh.core.collaboration;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;
import java.util.Map;

/**
 * Agent 间消息。
 * 通过 MessageBus 在 Agent 之间传递，支持多种消息类型。
 *
 * 每条消息强制携带 traceId 和 collaborationId，用于全链路追踪。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentMessage {

    /** 消息唯一 ID */
    private String messageId;

    /** 发送方 Agent ID */
    private String fromAgentId;

    /** 接收方 Agent ID（null 表示广播） */
    private String toAgentId;

    /** 消息类型 */
    private MessageType type;

    /** 消息文本内容 */
    private String content;

    /** 结构化数据（工具调用结果、中间产物等） */
    private Map<String, Object> payload;

    /** 关联的会话 ID */
    private String sessionId;

    /** 协作流程 ID（一次多 Agent 协作的唯一标识） */
    private String collaborationId;

    /** 链路追踪 ID（跨 Agent 传递，与现有 TraceIdContext 一致） */
    private String traceId;

    /** 消息时间戳。
     * 注意：不使用 @Builder.Default，避免反序列化（JSON 恢复）时被覆盖为当前时间。
     * 构造消息时由 MessageBus.send()/broadcast() 自动补全。 */
    private Instant timestamp;

    /** 回复目标消息 ID（用于构建对话链） */
    private String replyToMessageId;

    public enum MessageType {
        /** 任务分配（Supervisor → Worker） */
        TASK_ASSIGNMENT,
        /** 任务完成通知（Worker → Supervisor） */
        TASK_COMPLETE,
        /** 任务失败通知（Worker → Supervisor） */
        TASK_FAILED,
        /** 查询请求 */
        QUERY,
        /** 查询响应 */
        RESPONSE,
        /** 委派请求（Agent → Agent） */
        DELEGATE,
        /** 上下文更新（广播） */
        CONTEXT_UPDATE,
        /** 辩论陈述 */
        DEBATE_STATEMENT,
        /** 辩论质疑 */
        DEBATE_CHALLENGE,
        /** 审批请求 */
        APPROVAL_REQUEST,
        /** 审批结果 */
        APPROVAL_RESULT,
        /** 心跳 */
        HEARTBEAT
    }
}
