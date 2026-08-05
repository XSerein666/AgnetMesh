package com.agentmesh.core.task;

import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JDBC 任务仓库实现
 * 使用乐观锁版本号保证并发安全
 * 当前为内存实现，生产环境需替换为 JDBC
 */
@Slf4j
public class JdbcTaskRepository implements TaskRepository {

    private final Map<String, Task> store = new ConcurrentHashMap<>();

    @Override
    public void save(Task task) {
        store.put(task.getTaskId(), task);
    }

    @Override
    public void updateStatus(String taskId, TaskStatus newStatus, Object output) {
        Task task = store.get(taskId);
        if (task == null) {
            return;
        }
        int currentVersion = task.getVersion();
        task.setStatus(newStatus);
        task.setOutput(output);
        task.setVersion(currentVersion + 1);
        task.setUpdatedAt(java.time.LocalDateTime.now());
    }

    @Override
    public Task findById(String taskId) {
        return store.get(taskId);
    }
}