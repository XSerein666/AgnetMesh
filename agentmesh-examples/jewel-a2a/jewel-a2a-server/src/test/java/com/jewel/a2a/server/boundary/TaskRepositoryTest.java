package com.jewel.a2a.server.boundary;

import com.agentmesh.core.task.Task;
import com.agentmesh.core.task.TaskRepository;
import com.agentmesh.core.task.TaskStatus;
import com.jewel.a2a.repository.entity.TaskEntity;
import com.jewel.a2a.repository.mapper.TaskMapper;
import com.jewel.a2a.server.service.MyBatisTaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * TaskRepository 边界测试：状态转换、null 值处理等。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TaskRepository 边界测试")
class TaskRepositoryTest {

    @Mock
    private TaskMapper taskMapper;

    @InjectMocks
    private MyBatisTaskRepository taskRepository;

    // ========== 状态转换测试 ==========

    @Nested
    @DisplayName("Task 状态转换")
    class TaskStatusTransition {

        @Test
        @DisplayName("新建 Task 默认状态为 PENDING")
        void shouldDefaultToPending() {
            Task task = new Task("task-1", "chat", Map.of("message", "hello"));
            assertEquals(TaskStatus.PENDING, task.getStatus());
        }

        @Test
        @DisplayName("PENDING → RUNNING 状态转换")
        void shouldTransitionFromPendingToRunning() {
            Task task = new Task("task-1", "chat", Map.of("message", "hello"));
            task.setStatus(TaskStatus.RUNNING);
            assertEquals(TaskStatus.RUNNING, task.getStatus());
        }

        @Test
        @DisplayName("RUNNING → SUCCESS 状态转换")
        void shouldTransitionFromRunningToSuccess() {
            Task task = new Task("task-1", "chat", Map.of("message", "hello"));
            task.setStatus(TaskStatus.RUNNING);
            task.setStatus(TaskStatus.SUCCESS);
            assertEquals(TaskStatus.SUCCESS, task.getStatus());
        }

        @Test
        @DisplayName("RUNNING → FAILED 状态转换")
        void shouldTransitionFromRunningToFailed() {
            Task task = new Task("task-1", "chat", Map.of("message", "hello"));
            task.setStatus(TaskStatus.RUNNING);
            task.setStatus(TaskStatus.FAILED);
            assertEquals(TaskStatus.FAILED, task.getStatus());
        }

        @Test
        @DisplayName("PENDING → FAILED 直接转换")
        void shouldTransitionFromPendingToFailed() {
            Task task = new Task("task-1", "chat", Map.of("message", "hello"));
            task.setStatus(TaskStatus.FAILED);
            assertEquals(TaskStatus.FAILED, task.getStatus());
        }
    }

    // ========== save 边界测试 ==========

    @Nested
    @DisplayName("save 边界测试")
    class SaveBoundary {

        @Test
        @DisplayName("save 应正确映射 Task → TaskEntity")
        void shouldMapTaskToEntity() {
            Task task = new Task("task-1", "chat", Map.of("message", "hello"));
            taskRepository.save(task);

            ArgumentCaptor<TaskEntity> captor = ArgumentCaptor.forClass(TaskEntity.class);
            verify(taskMapper).insert(captor.capture());

            TaskEntity entity = captor.getValue();
            assertEquals("task-1", entity.getTaskId());
            assertEquals("chat", entity.getSkillId());
            assertEquals(Map.of("message", "hello"), entity.getInput());
            assertEquals(com.jewel.a2a.common.enums.TaskStatus.PENDING, entity.getStatus());
            assertNotNull(entity.getCreatedAt());
            assertNotNull(entity.getUpdatedAt());
        }

        @Test
        @DisplayName("save 带 null createdAt 的 Task 应自动设置")
        void shouldAutoSetCreatedAt() {
            Task task = new Task("task-1", "chat", Map.of());
            // 手动设置 createdAt 为 null 模拟未设置情况
            task.setCreatedAt(null);

            taskRepository.save(task);

            ArgumentCaptor<TaskEntity> captor = ArgumentCaptor.forClass(TaskEntity.class);
            verify(taskMapper).insert(captor.capture());
            assertNotNull(captor.getValue().getCreatedAt());
        }

        @Test
        @DisplayName("save 带已有 createdAt 的 Task 应保留")
        void shouldPreserveExistingCreatedAt() {
            LocalDateTime customTime = LocalDateTime.of(2024, 1, 1, 12, 0);
            Task task = new Task("task-1", "chat", Map.of());
            task.setCreatedAt(customTime);

            taskRepository.save(task);

            ArgumentCaptor<TaskEntity> captor = ArgumentCaptor.forClass(TaskEntity.class);
            verify(taskMapper).insert(captor.capture());
            assertEquals(customTime, captor.getValue().getCreatedAt());
        }
    }

    // ========== updateStatus 边界测试 ==========

    @Nested
    @DisplayName("updateStatus 边界测试")
    class UpdateStatusBoundary {

        @Test
        @DisplayName("更新为 SUCCESS 应正确设置状态和输出")
        void shouldUpdateToSuccess() {
            Map<String, Object> output = Map.of("reply", "成功");
            taskRepository.updateStatus("task-1", TaskStatus.SUCCESS, output);

            ArgumentCaptor<TaskEntity> captor = ArgumentCaptor.forClass(TaskEntity.class);
            verify(taskMapper).update(captor.capture(), any());

            TaskEntity entity = captor.getValue();
            assertEquals(com.jewel.a2a.common.enums.TaskStatus.SUCCESS, entity.getStatus());
            assertEquals(output, entity.getOutput());
        }

        @Test
        @DisplayName("更新为 FAILED 应正确设置状态和错误信息")
        void shouldUpdateToFailed() {
            Map<String, Object> output = Map.of("error", "任务执行失败");
            taskRepository.updateStatus("task-1", TaskStatus.FAILED, output);

            ArgumentCaptor<TaskEntity> captor = ArgumentCaptor.forClass(TaskEntity.class);
            verify(taskMapper).update(captor.capture(), any());

            TaskEntity entity = captor.getValue();
            assertEquals(com.jewel.a2a.common.enums.TaskStatus.FAILED, entity.getStatus());
        }

        @Test
        @DisplayName("更新为 RUNNING 时 output 为 null 应正常处理")
        void shouldHandleNullOutput() {
            taskRepository.updateStatus("task-1", TaskStatus.RUNNING, null);

            ArgumentCaptor<TaskEntity> captor = ArgumentCaptor.forClass(TaskEntity.class);
            verify(taskMapper).update(captor.capture(), any());

            assertNull(captor.getValue().getOutput());
        }
    }

    // ========== findById 边界测试 ==========

    @Nested
    @DisplayName("findById 边界测试")
    class FindByIdBoundary {

        @Test
        @DisplayName("不存在的 taskId 应返回 null")
        void shouldReturnNullForUnknownTaskId() {
            when(taskMapper.selectOne(any())).thenReturn(null);

            Task result = taskRepository.findById("unknown-task");
            assertNull(result);
        }

        @Test
        @DisplayName("存在的 taskId 应返回完整 Task")
        void shouldReturnCompleteTask() {
            TaskEntity entity = TaskEntity.builder()
                    .taskId("task-1")
                    .skillId("chat")
                    .input(Map.of("message", "hello"))
                    .status(com.jewel.a2a.common.enums.TaskStatus.SUCCESS)
                    .output(Map.of("reply", "成功"))
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            when(taskMapper.selectOne(any())).thenReturn(entity);

            Task result = taskRepository.findById("task-1");

            assertNotNull(result);
            assertEquals("task-1", result.getTaskId());
            assertEquals("chat", result.getSkillId());
            assertEquals(TaskStatus.SUCCESS, result.getStatus());
            assertEquals(Map.of("reply", "成功"), result.getOutput());
        }
    }
}