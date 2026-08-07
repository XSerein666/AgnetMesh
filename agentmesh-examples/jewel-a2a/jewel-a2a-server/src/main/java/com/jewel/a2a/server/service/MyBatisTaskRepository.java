package com.jewel.a2a.server.service;

import com.agentmesh.core.task.Task;
import com.agentmesh.core.task.TaskRepository;
import com.agentmesh.core.task.TaskStatus;
import com.jewel.a2a.repository.entity.TaskEntity;
import com.jewel.a2a.repository.mapper.TaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * AgentMesh TaskRepository 适配器。
 * <p>
 * 实现 AgentMesh 的 TaskRepository 接口，底层委托给 MyBatis-Plus 的 TaskMapper。
 * 字段映射：Task ↔ TaskEntity（忽略 Task.version 和 TaskEntity.id/errorMessage）。
 */
@Slf4j
@Component("myBatisTaskRepository")
@RequiredArgsConstructor
public class MyBatisTaskRepository implements TaskRepository {

    private final TaskMapper taskMapper;

    @Override
    public void save(Task task) {
        TaskEntity entity = TaskEntity.builder()
                .taskId(task.getTaskId())
                .skillId(task.getSkillId())
                .input(task.getInput())
                .status(mapStatus(task.getStatus()))
                .createdAt(task.getCreatedAt() != null ? task.getCreatedAt() : LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        taskMapper.insert(entity);
        log.debug("[TaskRepository] 保存任务: taskId={}", task.getTaskId());
    }

    @Override
    public void updateStatus(String taskId, TaskStatus status, Object output) {
        TaskEntity entity = TaskEntity.builder()
                .status(mapStatus(status))
                .output(output)
                .updatedAt(LocalDateTime.now())
                .build();
        taskMapper.update(entity,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<TaskEntity>()
                        .eq(TaskEntity::getTaskId, taskId));
        log.debug("[TaskRepository] 更新任务状态: taskId={}, status={}", taskId, status);
    }

    @Override
    public Task findById(String taskId) {
        TaskEntity entity = taskMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<TaskEntity>()
                        .eq(TaskEntity::getTaskId, taskId));
        if (entity == null) {
            return null;
        }
        Task task = new Task(entity.getTaskId(), entity.getSkillId(), entity.getInput());
        task.setStatus(mapStatus(entity.getStatus()));
        task.setOutput(entity.getOutput());
        task.setCreatedAt(entity.getCreatedAt());
        task.setUpdatedAt(entity.getUpdatedAt());
        return task;
    }

    private com.jewel.a2a.common.enums.TaskStatus mapStatus(TaskStatus status) {
        return switch (status) {
            case PENDING -> com.jewel.a2a.common.enums.TaskStatus.PENDING;
            case RUNNING -> com.jewel.a2a.common.enums.TaskStatus.RUNNING;
            case SUCCESS -> com.jewel.a2a.common.enums.TaskStatus.SUCCESS;
            case FAILED -> com.jewel.a2a.common.enums.TaskStatus.FAILED;
        };
    }

    private TaskStatus mapStatus(com.jewel.a2a.common.enums.TaskStatus status) {
        return switch (status) {
            case PENDING -> TaskStatus.PENDING;
            case RUNNING -> TaskStatus.RUNNING;
            case SUCCESS -> TaskStatus.SUCCESS;
            case FAILED -> TaskStatus.FAILED;
        };
    }
}
