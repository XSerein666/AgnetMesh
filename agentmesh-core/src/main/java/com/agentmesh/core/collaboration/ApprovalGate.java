package com.agentmesh.core.collaboration;

import com.agentmesh.core.infrastructure.TraceIdContext;
import com.agentmesh.core.llm.StreamEvent;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * 审批门控。
 *
 * 负责 Human-in-the-Loop 审批流程的暂停/恢复/超时处理。
 *
 * 采用"流程结束 + 回调重新触发"模式，不挂起线程：
 * 1. 保存 WorkflowState 到持久化存储
 * 2. 发送审批通知
 * 3. 返回 APPROVAL_PENDING 事件，流程结束
 * 4. 审批回调时重新加载状态，恢复执行
 */
@Slf4j
public class ApprovalGate {

    private final WorkflowStateStore stateStore;
    private final Duration defaultExpireTime;
    private final CollaborationMetrics collabMetrics;

    public ApprovalGate(WorkflowStateStore stateStore,
                         Duration defaultExpireTime,
                         CollaborationMetrics collabMetrics) {
        this.stateStore = stateStore;
        this.defaultExpireTime = defaultExpireTime;
        this.collabMetrics = collabMetrics;
    }

    /**
     * 请求审批。
     * 保存状态 → 发送通知 → 返回 PENDING 事件。
     *
     * @param request 审批请求
     * @param sharedContext 当前共享上下文
     * @param completedWorkerResults 已完成的 Worker 结果
     * @return SSE 事件流
     */
    public Flux<StreamEvent> requestApproval(ApprovalRequest request,
                                              SharedContext sharedContext,
                                              Map<String, WorkerResult> completedWorkerResults) {
        String traceId = TraceIdContext.get();
        log.info("[HITL] 请求审批: approvalId={}, node={}, collaborationId={}, traceId={}",
                request.getApprovalId(), request.getApprovalNode(),
                request.getCollaborationId(), traceId);

        Instant expireTime = request.getExpireTime() != null
                ? request.getExpireTime()
                : Instant.now().plus(defaultExpireTime);

        WorkflowState state = WorkflowState.builder()
                .collaborationId(request.getCollaborationId())
                .approvalId(request.getApprovalId())
                .status(WorkflowState.Status.AWAITING_APPROVAL)
                .approvalNode(request.getApprovalNode())
                .approvalContent(request.getContent())
                .requestTime(Instant.now())
                .expireTime(expireTime)
                .completedWorkerResults(completedWorkerResults)
                .contextSnapshot(sharedContext.snapshot())
                .callbackUrl(request.getCallbackUrl())
                .notifyChannel(request.getNotifyChannel())
                .build();

        stateStore.save(request.getCollaborationId(), state);

        return Flux.just(
                StreamEvent.builder()
                        .type(StreamEvent.Type.THINKING)
                        .content("审批请求已发送: " + request.getApprovalNode())
                        .build(),
                StreamEvent.builder()
                        .type(StreamEvent.Type.TEXT)
                        .content("## 需要人工审批\n\n**节点**: " + request.getApprovalNode()
                                + "\n**内容**: " + request.getContent()
                                + "\n**审批ID**: " + request.getApprovalId()
                                + "\n**过期时间**: " + expireTime
                                + "\n\n请等待审批完成后继续...")
                        .build(),
                StreamEvent.builder()
                        .type(StreamEvent.Type.DONE)
                        .build()
        );
    }

    /**
     * 处理审批回调。
     * 加载状态 → 恢复上下文 → 返回结果。
     *
     * @param callback 审批回调
     * @return 恢复后的 SSE 事件流
     */
    public Mono<ApprovalResult> handleCallback(ApprovalCallback callback) {
        String traceId = TraceIdContext.get();
        log.info("[HITL] 收到审批回调: approvalId={}, collaborationId={}, result={}, traceId={}",
                callback.getApprovalId(), callback.getCollaborationId(),
                callback.getResult(), traceId);

        return Mono.fromCallable(() -> {
            var stateOpt = stateStore.load(callback.getCollaborationId());
            if (stateOpt.isEmpty()) {
                log.warn("[HITL] 状态不存在或已过期: collaborationId={}", callback.getCollaborationId());
                return ApprovalResult.builder()
                        .success(false)
                        .message("状态不存在或已过期")
                        .build();
            }

            WorkflowState state = stateOpt.get();

            // 幂等检查：已处理则忽略
            if (state.getStatus() == WorkflowState.Status.APPROVED
                    || state.getStatus() == WorkflowState.Status.REJECTED) {
                log.info("[HITL] 审批已处理，忽略重复回调: collaborationId={}, currentStatus={}",
                        callback.getCollaborationId(), state.getStatus());
                return ApprovalResult.builder()
                        .success(true)
                        .alreadyProcessed(true)
                        .message("审批已处理，无需重复操作")
                        .build();
            }

            if (callback.getResult() == ApprovalCallback.Result.APPROVED) {
                state.setStatus(WorkflowState.Status.APPROVED);
                log.info("[HITL] 审批通过: collaborationId={}, node={}",
                        callback.getCollaborationId(), state.getApprovalNode());
            } else {
                state.setStatus(WorkflowState.Status.REJECTED);
                log.info("[HITL] 审批驳回: collaborationId={}, node={}",
                        callback.getCollaborationId(), state.getApprovalNode());
            }

            stateStore.save(callback.getCollaborationId(), state);

            return ApprovalResult.builder()
                    .success(true)
                    .workflowState(state)
                    .message(callback.getResult() == ApprovalCallback.Result.APPROVED
                            ? "审批通过" : "审批驳回")
                    .build();
        });
    }

    /**
     * 检查并处理过期审批。
     * 由定时任务调用。
     */
    public int processExpiredApprovals() {
        return stateStore.cleanupExpired(defaultExpireTime);
    }

    @lombok.Builder
    @lombok.Data
    public static class ApprovalResult {
        private boolean success;
        private boolean alreadyProcessed;
        private String message;
        private WorkflowState workflowState;
    }
}
