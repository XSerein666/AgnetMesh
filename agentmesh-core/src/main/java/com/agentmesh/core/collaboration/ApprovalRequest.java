package com.agentmesh.core.collaboration;

import lombok.Builder;
import lombok.Data;
import java.time.Instant;
import java.util.Map;

/**
 * 审批请求实体。
 * 由 Supervisor 在需要人工审批时创建，通过 MessageBus 发送给审批者。
 */
@Data
@Builder
public class ApprovalRequest {

    /** 审批 ID */
    private String approvalId;

    /** 协作流程 ID */
    private String collaborationId;

    /** 审批节点名称 */
    private String approvalNode;

    /** 审批内容 */
    private String content;

    /** 审批选项 */
    private Map<String, String> options;

    /** 审批请求时间 */
    @Builder.Default
    private Instant requestTime = Instant.now();

    /** 审批超时时间 */
    private Instant expireTime;

    /** 审批回调 URL */
    private String callbackUrl;

    /** 通知渠道 */
    private String notifyChannel;
}
