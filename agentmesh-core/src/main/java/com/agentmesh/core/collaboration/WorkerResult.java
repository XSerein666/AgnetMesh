package com.agentmesh.core.collaboration;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Map;

/**
 * Worker 执行结果的统一协议。
 * 所有 Worker 必须返回此结构，由框架层做格式校验。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkerResult {

    /** 对应的子任务 ID */
    private String taskId;

    /** Worker ID */
    private String workerId;

    /** 执行状态 */
    private Status status;

    /** 结果文本（成功时） */
    private String content;

    /** 结构化数据（成功时） */
    private Map<String, Object> data;

    /** 错误信息（失败时） */
    private String errorMessage;

    /** 结构化错误详情（失败时） */
    private ErrorInfo errorInfo;

    /** 执行耗时（毫秒） */
    private long durationMs;

    /**
     * 结构化错误信息。
     * 当 status 为 FAILED/TIMEOUT/UNABLE_TO_HANDLE 时填充，用于 Supervisor 决策重试/跳过/终止。
     */
    @Data
    @Builder
    public static class ErrorInfo {
        /** 错误码（框架预定义） */
        private String errorCode;
        /** 错误描述 */
        private String description;
        /** 是否可重试 */
        private boolean retryable;
        /** 建议的重试等待时间（毫秒），-1 表示不重试 */
        private long retryAfterMs;
    }

    public enum Status {
        SUCCESS,
        FAILED,
        TIMEOUT,
        /** Worker 无法处理该任务（不匹配） */
        UNABLE_TO_HANDLE
    }
}
