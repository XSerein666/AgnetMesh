package com.agentmesh.core.collaboration;

import lombok.Builder;
import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * Supervisor 任务拆解的结构化输出。
 * Supervisor 通过 LLM Function Calling 输出此结构，而非自由文本。
 *
 * 校验规则：
 * 1. subTasks 不能为空（由框架层校验，非 LLM 保证）
 * 2. 每个 subTask.assignedWorkerId 必须在 delegateTo 列表中
 * 3. 无法匹配的 Worker → 不分配，标记为 UNASSIGNED → Supervisor 自行处理
 */
@Data
@Builder
public class TaskDecompositionResult {

    /** 原始用户输入 */
    private String originalInput;

    /** 拆解后的子任务列表 */
    private List<SubTask> subTasks;

    /** 拆解策略说明（用于日志和调试） */
    private String strategy;

    @Data
    @Builder
    public static class SubTask {
        /** 子任务 ID */
        private String taskId;
        /** 子任务描述 */
        private String description;
        /** 分配的 Worker ID（null 表示 Supervisor 自行处理） */
        private String assignedWorkerId;
        /** 子任务输入参数 */
        private Map<String, Object> input;
        /** 依赖的子任务 ID 列表（无依赖则为空） */
        @Builder.Default
        private List<String> dependsOn = List.of();
        /** 优先级（数字越小越优先） */
        @Builder.Default
        private int priority = 5;
    }
}
