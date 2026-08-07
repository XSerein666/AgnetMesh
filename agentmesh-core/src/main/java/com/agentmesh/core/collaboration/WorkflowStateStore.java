package com.agentmesh.core.collaboration;

import java.time.Duration;
import java.util.Optional;

/**
 * 工作流状态持久化存储。
 *
 * 用于 HITL 审批等待期间的状态保存，支持：
 * - 应用重启后恢复
 * - 进程崩溃后恢复
 * - 跨天审批等待
 *
 * Phase 4 提供文件存储实现（FileWorkflowStateStore），
 * 后续可替换为 DB 实现（JdbcWorkflowStateStore）。
 */
public interface WorkflowStateStore {

    /**
     * 保存工作流状态。
     * @param collaborationId 协作流程 ID
     * @param state 序列化后的状态数据
     */
    void save(String collaborationId, WorkflowState state);

    /**
     * 加载工作流状态。
     * @param collaborationId 协作流程 ID
     * @return 状态数据（不存在或已过期则返回 empty）
     */
    Optional<WorkflowState> load(String collaborationId);

    /**
     * 删除工作流状态（审批完成后清理）。
     */
    void delete(String collaborationId);

    /**
     * 清理过期状态（定时任务调用）。
     * @param maxAge 最大保留时间
     * @return 清理数量
     */
    int cleanupExpired(Duration maxAge);
}
