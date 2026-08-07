package com.jewel.a2a.server.service;

import com.jewel.a2a.common.dto.TaskRequest;
import com.jewel.a2a.common.dto.TaskResponse;
import com.jewel.a2a.common.enums.TaskStatus;
import com.jewel.a2a.common.exception.A2AException;
import com.jewel.a2a.repository.entity.TaskEntity;
import com.jewel.a2a.repository.mapper.TaskMapper;
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
 * TaskService 单元测试：任务提交、查询、异常处理。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TaskService 单元测试")
class TaskServiceTest {

    @Mock
    private TaskMapper taskMapper;

    @Mock
    private TaskExecutorService taskExecutorService;

    @InjectMocks
    private TaskService taskService;

    // ========== submitTask ==========

    @Nested
    @DisplayName("任务提交")
    class SubmitTask {

        @Test
        @DisplayName("应返回正确的 TaskResponse（PENDING 状态）")
        void shouldReturnPendingTaskResponse() {
            TaskRequest request = TaskRequest.builder()
                    .skillId("chat")
                    .input(Map.of("message", "你好"))
                    .build();

            TaskResponse response = taskService.submitTask(request);

            assertNotNull(response);
            assertNotNull(response.getTaskId());
            assertTrue(response.getTaskId().startsWith("task_"));
            assertEquals(TaskStatus.PENDING, response.getStatus());
            assertEquals("任务已提交，请通过 SSE 订阅结果", response.getMessage());
        }

        @Test
        @DisplayName("应保存 TaskEntity 到数据库")
        void shouldSaveTaskEntity() {
            TaskRequest request = TaskRequest.builder()
                    .skillId("generate_jewelry_design")
                    .input(Map.of("prompt", "设计一枚钻戒"))
                    .build();

            taskService.submitTask(request);

            ArgumentCaptor<TaskEntity> captor = ArgumentCaptor.forClass(TaskEntity.class);
            verify(taskMapper).insert(captor.capture());
            TaskEntity entity = captor.getValue();

            assertEquals("generate_jewelry_design", entity.getSkillId());
            assertEquals(Map.of("prompt", "设计一枚钻戒"), entity.getInput());
            assertEquals(TaskStatus.PENDING, entity.getStatus());
            assertNotNull(entity.getCreatedAt());
            assertNotNull(entity.getUpdatedAt());
        }

        @Test
        @DisplayName("应异步执行任务")
        void shouldExecuteTaskAsync() {
            TaskRequest request = TaskRequest.builder()
                    .skillId("chat")
                    .input(Map.of("message", "hello"))
                    .build();

            TaskResponse response = taskService.submitTask(request);

            verify(taskExecutorService).executeTask(
                    response.getTaskId(), "chat", Map.of("message", "hello"));
        }

        @Test
        @DisplayName("不同请求应生成不同 taskId")
        void shouldGenerateUniqueTaskIds() {
            TaskRequest request = TaskRequest.builder()
                    .skillId("chat")
                    .input(Map.of())
                    .build();

            TaskResponse r1 = taskService.submitTask(request);
            TaskResponse r2 = taskService.submitTask(request);

            assertNotEquals(r1.getTaskId(), r2.getTaskId());
        }

        @Test
        @DisplayName("空 input 应正常处理")
        void shouldHandleEmptyInput() {
            TaskRequest request = TaskRequest.builder()
                    .skillId("chat")
                    .input(Map.of())
                    .build();

            TaskResponse response = taskService.submitTask(request);

            assertNotNull(response);
            assertEquals(TaskStatus.PENDING, response.getStatus());
        }

        @Test
        @DisplayName("null input 应正常处理")
        void shouldHandleNullInput() {
            TaskRequest request = TaskRequest.builder()
                    .skillId("chat")
                    .input(null)
                    .build();

            TaskResponse response = taskService.submitTask(request);

            assertNotNull(response);
            assertEquals(TaskStatus.PENDING, response.getStatus());
        }
    }

    // ========== getTask ==========

    @Nested
    @DisplayName("任务查询")
    class GetTask {

        @Test
        @DisplayName("存在的 taskId 应返回 TaskEntity")
        void shouldReturnTaskEntity() {
            TaskEntity entity = TaskEntity.builder()
                    .taskId("task-abc123")
                    .skillId("chat")
                    .input(Map.of("message", "hello"))
                    .status(TaskStatus.SUCCESS)
                    .output(Map.of("reply", "你好"))
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            when(taskMapper.selectOne(any())).thenReturn(entity);

            TaskEntity result = taskService.getTask("task-abc123");

            assertNotNull(result);
            assertEquals("task-abc123", result.getTaskId());
            assertEquals("chat", result.getSkillId());
            assertEquals(TaskStatus.SUCCESS, result.getStatus());
            assertEquals(Map.of("reply", "你好"), result.getOutput());
        }

        @Test
        @DisplayName("不存在的 taskId 应抛出 A2AException")
        void shouldThrowExceptionForUnknownTask() {
            when(taskMapper.selectOne(any())).thenReturn(null);

            A2AException ex = assertThrows(A2AException.class,
                    () -> taskService.getTask("unknown-task"));

            assertEquals(404, ex.getCode());
            assertTrue(ex.getMessage().contains("unknown-task"));
        }

        @Test
        @DisplayName("FAILED 状态的任务应查询到 errorMessage")
        void shouldReturnFailedTaskWithError() {
            TaskEntity entity = TaskEntity.builder()
                    .taskId("task-failed")
                    .skillId("chat")
                    .status(TaskStatus.FAILED)
                    .errorMessage("执行超时")
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            when(taskMapper.selectOne(any())).thenReturn(entity);

            TaskEntity result = taskService.getTask("task-failed");

            assertEquals(TaskStatus.FAILED, result.getStatus());
            assertEquals("执行超时", result.getErrorMessage());
        }
    }
}