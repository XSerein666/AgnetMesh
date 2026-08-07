package com.agentmesh.core.collaboration;

import lombok.Builder;
import lombok.Data;
import java.time.Instant;

/**
 * 审批回调结果。
 * 审批完成后由回调接口接收，用于恢复协作流程。
 */
@Data
@Builder
public class ApprovalCallback {

    /** 审批 ID */
    private String approvalId;

    /** 协作流程 ID */
    private String collaborationId;

    /** 审批结果 */
    private Result result;

    /** 审批意见 */
    private String comment;

    /** 审批人 */
    private String approver;

    /** 审批时间 */
    @Builder.Default
    private Instant approvalTime = Instant.now();

    public enum Result {
        APPROVED,
        REJECTED
    }
}
