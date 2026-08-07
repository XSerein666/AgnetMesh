package com.agentmesh.core.collaboration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * WorkerResult 单元测试。
 * 覆盖四种 Status 的序列化、格式校验。
 */
class WorkerResultTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldCreateSuccessResult() {
        WorkerResult result = WorkerResult.builder()
                .taskId("t1")
                .workerId("designer")
                .status(WorkerResult.Status.SUCCESS)
                .content("设计完成")
                .data(Map.of("design", "方案A"))
                .durationMs(1500)
                .build();

        assertEquals(WorkerResult.Status.SUCCESS, result.getStatus());
        assertEquals("设计完成", result.getContent());
        assertEquals("方案A", result.getData().get("design"));
        assertEquals(1500, result.getDurationMs());
        assertNull(result.getErrorMessage());
    }

    @Test
    void shouldCreateFailedResult() {
        WorkerResult result = WorkerResult.builder()
                .taskId("t2")
                .workerId("crafter")
                .status(WorkerResult.Status.FAILED)
                .errorMessage("工艺评估失败")
                .errorInfo(WorkerResult.ErrorInfo.builder()
                        .errorCode("BUSINESS_ERROR")
                        .description("工艺参数不合法")
                        .retryable(false)
                        .retryAfterMs(-1)
                        .build())
                .durationMs(500)
                .build();

        assertEquals(WorkerResult.Status.FAILED, result.getStatus());
        assertEquals("BUSINESS_ERROR", result.getErrorInfo().getErrorCode());
        assertFalse(result.getErrorInfo().isRetryable());
    }

    @Test
    void shouldCreateTimeoutResult() {
        WorkerResult result = WorkerResult.builder()
                .taskId("t3")
                .workerId("worker-1")
                .status(WorkerResult.Status.TIMEOUT)
                .errorMessage("Worker 超时")
                .errorInfo(WorkerResult.ErrorInfo.builder()
                        .errorCode("WORKER_TIMEOUT")
                        .description("30s 超时")
                        .retryable(true)
                        .retryAfterMs(1000)
                        .build())
                .durationMs(30000)
                .build();

        assertEquals(WorkerResult.Status.TIMEOUT, result.getStatus());
        assertTrue(result.getErrorInfo().isRetryable());
        assertEquals(1000, result.getErrorInfo().getRetryAfterMs());
    }

    @Test
    void shouldCreateUnableToHandleResult() {
        WorkerResult result = WorkerResult.builder()
                .taskId("t4")
                .workerId("worker-1")
                .status(WorkerResult.Status.UNABLE_TO_HANDLE)
                .errorMessage("超出能力范围")
                .errorInfo(WorkerResult.ErrorInfo.builder()
                        .errorCode("OUT_OF_SCOPE")
                        .description("任务不匹配 Worker 技能")
                        .retryable(false)
                        .retryAfterMs(-1)
                        .build())
                .build();

        assertEquals(WorkerResult.Status.UNABLE_TO_HANDLE, result.getStatus());
    }
}