package com.jewel.a2a.server.service;

import com.jewel.a2a.common.dto.TaskRequest;
import com.jewel.a2a.common.dto.TaskResponse;
import com.jewel.a2a.common.enums.TaskStatus;
import com.jewel.a2a.common.exception.A2AException;
import com.jewel.a2a.repository.entity.TaskEntity;
import com.jewel.a2a.repository.mapper.TaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 任务调度核心：创建任务、异步执行、推送结果
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskMapper taskMapper;
    private final TaskExecutorService taskExecutorService;

    /**
     * 提交任务，秒级返回 taskId
     */
    @Transactional
    public TaskResponse submitTask(TaskRequest request) {
        String taskId = "task_" + UUID.randomUUID().toString().substring(0, 8);
        LocalDateTime now = LocalDateTime.now();

        TaskEntity entity = TaskEntity.builder()
                .taskId(taskId)
                .skillId(request.getSkillId())
                .input(request.getInput())
                .status(TaskStatus.PENDING)
                .createdAt(now)
                .updatedAt(now)
                .build();
        taskMapper.insert(entity);

        log.info("任务已提交: taskId={}, skillId={}", taskId, request.getSkillId());

        // 通过独立 Bean 异步执行，确保 @Async 代理生效
        taskExecutorService.executeTask(taskId, request.getSkillId(), request.getInput());

        return TaskResponse.builder()
                .taskId(taskId)
                .status(TaskStatus.PENDING)
                .message("任务已提交，请通过 SSE 订阅结果")
                .build();
    }

    /**
     * 查询任务状态（轮询用）
     */
    public TaskEntity getTask(String taskId) {
        TaskEntity entity = taskMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<TaskEntity>()
                        .eq(TaskEntity::getTaskId, taskId)
        );
        if (entity == null) {
            throw new A2AException(404, "任务不存在: " + taskId);
        }
        return entity;
    }
}
