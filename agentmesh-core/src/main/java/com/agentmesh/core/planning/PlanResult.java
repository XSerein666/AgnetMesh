package com.agentmesh.core.planning;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 计划执行结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanResult {

    /** 计划 ID */
    private String planId;

    /** 是否全部成功 */
    private boolean allSuccess;

    /** 完成的子任务数 */
    private int completedCount;

    /** 失败的子任务数 */
    private int failedCount;

    /** 聚合文本结果 */
    private String summary;

    /** 每个子任务的详细结果 */
    private List<SubTaskResult> subTaskResults;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubTaskResult {
        private String subTaskId;
        private String description;
        private SubTaskStatus status;
        private String result;
        private String error;
    }
}