package com.agentmesh.core.collaboration;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;
import java.util.Map;

/**
 * 工作流状态快照。
 * 包含恢复协作流程所需的所有信息。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowState {

    /** 协作流程 ID */
    private String collaborationId;

    /** 审批请求 ID */
    private String approvalId;

    /** 当前状态 */
    private Status status;

    /** 审批节点名称（如 "工艺可行性确认"） */
    private String approvalNode;

    /** 审批内容（展示给人工审批者） */
    private String approvalContent;

    /** 审批请求时间 */
    private Instant requestTime;

    /** 审批超时时间（超时后自动拒绝） */
    private Instant expireTime;

    /** 已完成的子任务结果（用于恢复时跳过已完成步骤） */
    private Map<String, WorkerResult> completedWorkerResults;

    /** SharedContext 快照（用于恢复时重建上下文） */
    private Map<String, Object> contextSnapshot;

    /** 审批回调 URL（审批完成后调用） */
    private String callbackUrl;

    /** 审批通知渠道（飞书/邮件/控制台） */
    private String notifyChannel;

    public enum Status {
        AWAITING_APPROVAL,  // 等待审批
        APPROVED,           // 已批准
        REJECTED,           // 已驳回
        EXPIRED,            // 已过期
        CANCELLED           // 已取消
    }
}
