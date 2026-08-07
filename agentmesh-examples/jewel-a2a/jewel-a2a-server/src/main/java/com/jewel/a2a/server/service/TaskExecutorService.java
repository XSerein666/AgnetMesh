package com.jewel.a2a.server.service;

import com.jewel.a2a.common.dto.TaskEvent;
import com.jewel.a2a.common.enums.TaskStatus;
import com.jewel.a2a.repository.entity.TaskEntity;
import com.jewel.a2a.repository.mapper.TaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 异步任务执行器（独立 Bean，确保 @Async 代理生效）
 * 使用 AgentMesh 的 ToolRegistry 进行工具调度
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskExecutorService {

    private final TaskMapper taskMapper;
    private final SseEmitterService sseEmitterService;
    private final com.agentmesh.core.tool.ToolRegistry toolRegistry;

    @Async
    public void executeTask(String taskId, String skillId, Map<String, Object> input) {
        try {
            updateTaskStatus(taskId, TaskStatus.RUNNING, null, null);
            sseEmitterService.sendEvent(taskId, TaskEvent.builder()
                    .taskId(taskId)
                    .status(TaskStatus.RUNNING)
                    .message("正在执行 " + skillId + " ...")
                    .build());

            // AgentMesh ToolRegistry.execute() 自动处理工具查找、超时、异常
            Object result = toolRegistry.execute(skillId, input);
            log.info("任务执行成功: taskId={}", taskId);

            updateTaskStatus(taskId, TaskStatus.SUCCESS, result, null);
            sseEmitterService.complete(taskId, TaskEvent.builder()
                    .taskId(taskId)
                    .status(TaskStatus.SUCCESS)
                    .output(result)
                    .build());

        } catch (Exception e) {
            log.error("任务执行失败: taskId={}", taskId, e);
            updateTaskStatus(taskId, TaskStatus.FAILED, null, e.getMessage());
            sseEmitterService.complete(taskId, TaskEvent.builder()
                    .taskId(taskId)
                    .status(TaskStatus.FAILED)
                    .message(e.getMessage())
                    .build());
        }
    }

    private void updateTaskStatus(String taskId, TaskStatus status, Object output, String errorMessage) {
        TaskEntity entity = TaskEntity.builder()
                .status(status)
                .output(output)
                .errorMessage(errorMessage)
                .updatedAt(LocalDateTime.now())
                .build();
        taskMapper.update(entity,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<TaskEntity>()
                        .eq(TaskEntity::getTaskId, taskId));
    }
}