package com.jewel.a2a.server.service;

import com.jewel.a2a.common.dto.TaskEvent;
import com.jewel.a2a.common.enums.TaskStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SseEmitterService 单元测试：SSE 连接生命周期管理。
 */
@DisplayName("SseEmitterService 单元测试")
class SseEmitterServiceTest {

    private final SseEmitterService sseEmitterService = new SseEmitterService();

    // ========== createEmitter ==========

    @Nested
    @DisplayName("创建 SSE 连接")
    class CreateEmitter {

        @Test
        @DisplayName("应返回非 null 的 SseEmitter")
        void shouldReturnNonNullEmitter() {
            SseEmitter emitter = sseEmitterService.createEmitter("task-1");

            assertNotNull(emitter);
        }

        @Test
        @DisplayName("超时时间应为 120 秒")
        void shouldHave120SecondTimeout() {
            SseEmitter emitter = sseEmitterService.createEmitter("task-1");

            assertEquals(120_000L, emitter.getTimeout());
        }

        @Test
        @DisplayName("不同 taskId 应创建不同的 emitter")
        void shouldCreateDifferentEmittersForDifferentTaskIds() {
            SseEmitter e1 = sseEmitterService.createEmitter("task-1");
            SseEmitter e2 = sseEmitterService.createEmitter("task-2");

            assertNotNull(e1);
            assertNotNull(e2);
            assertNotSame(e1, e2);
        }
    }

    // ========== sendEvent ==========

    @Nested
    @DisplayName("发送事件")
    class SendEvent {

        @Test
        @DisplayName("不存在的 emitter 应静默跳过（不抛异常）")
        void shouldSilentlySkipForUnknownTaskId() {
            TaskEvent event = TaskEvent.builder()
                    .taskId("unknown-task")
                    .status(TaskStatus.RUNNING)
                    .message("test")
                    .build();

            assertDoesNotThrow(() -> sseEmitterService.sendEvent("unknown-task", event));
        }

        @Test
        @DisplayName("存在的 emitter 应能发送事件")
        void shouldSendEventToExistingEmitter() {
            SseEmitter emitter = sseEmitterService.createEmitter("task-1");
            TaskEvent event = TaskEvent.builder()
                    .taskId("task-1")
                    .status(TaskStatus.RUNNING)
                    .message("正在执行...")
                    .build();

            assertDoesNotThrow(() -> sseEmitterService.sendEvent("task-1", event));
        }
    }

    // ========== complete ==========

    @Nested
    @DisplayName("完成 SSE 连接")
    class Complete {

        @Test
        @DisplayName("不存在的 emitter 应静默跳过")
        void shouldSilentlySkipForUnknownTaskId() {
            TaskEvent event = TaskEvent.builder()
                    .taskId("unknown-task")
                    .status(TaskStatus.SUCCESS)
                    .output(Map.of("reply", "完成"))
                    .build();

            assertDoesNotThrow(() -> sseEmitterService.complete("unknown-task", event));
        }

        @Test
        @DisplayName("完成已存在的 emitter 应正常关闭")
        void shouldCompleteExistingEmitter() {
            sseEmitterService.createEmitter("task-1");
            TaskEvent event = TaskEvent.builder()
                    .taskId("task-1")
                    .status(TaskStatus.SUCCESS)
                    .build();

            assertDoesNotThrow(() -> sseEmitterService.complete("task-1", event));
        }
    }

    // ========== completeWithError ==========

    @Nested
    @DisplayName("异常完成 SSE 连接")
    class CompleteWithError {

        @Test
        @DisplayName("不存在的 emitter 应静默跳过")
        void shouldSilentlySkipForUnknownTaskId() {
            RuntimeException error = new RuntimeException("测试异常");

            assertDoesNotThrow(() -> sseEmitterService.completeWithError("unknown-task", error));
        }

        @Test
        @DisplayName("存在的 emitter 应以异常方式关闭")
        void shouldCompleteWithErrorForExistingEmitter() {
            sseEmitterService.createEmitter("task-1");
            RuntimeException error = new RuntimeException("LLM 调用超时");

            assertDoesNotThrow(() -> sseEmitterService.completeWithError("task-1", error));
        }

        @Test
        @DisplayName("IOException 异常 emitter 应正常处理")
        void shouldHandleIOException() {
            sseEmitterService.createEmitter("task-1");
            IOException ioError = new IOException("连接中断");

            assertDoesNotThrow(() -> sseEmitterService.completeWithError("task-1", ioError));
        }
    }

    // ========== 生命周期 ==========

    @Nested
    @DisplayName("SSE 生命周期")
    class Lifecycle {

        @Test
        @DisplayName("创建 → 发送 → 完成 完整流程")
        void shouldHandleFullLifecycle() {
            String taskId = "task-lifecycle";

            // 创建
            SseEmitter emitter = sseEmitterService.createEmitter(taskId);
            assertNotNull(emitter);

            // 发送
            assertDoesNotThrow(() -> sseEmitterService.sendEvent(taskId,
                    TaskEvent.builder().taskId(taskId).status(TaskStatus.RUNNING).message("处理中").build()));

            // 完成
            assertDoesNotThrow(() -> sseEmitterService.complete(taskId,
                    TaskEvent.builder().taskId(taskId).status(TaskStatus.SUCCESS).build()));
        }

        @Test
        @DisplayName("完成后再发送应静默跳过")
        void shouldSkipSendAfterComplete() {
            String taskId = "task-after-complete";

            sseEmitterService.createEmitter(taskId);
            sseEmitterService.complete(taskId,
                    TaskEvent.builder().taskId(taskId).status(TaskStatus.SUCCESS).build());

            // 完成后再发送不应抛异常
            assertDoesNotThrow(() -> sseEmitterService.sendEvent(taskId,
                    TaskEvent.builder().taskId(taskId).status(TaskStatus.RUNNING).message("迟到的消息").build()));
        }
    }
}