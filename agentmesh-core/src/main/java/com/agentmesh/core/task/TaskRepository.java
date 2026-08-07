package com.agentmesh.core.task;

/**
 * 任务持久化接口
 */
public interface TaskRepository {

    /** 保存任务 */
    void save(Task task);

    /** 更新任务状态和输出 */
    void updateStatus(String taskId, TaskStatus status, Object output);

    /** 根据 ID 查询任务 */
    Task findById(String taskId);
}
