package com.agentmesh.core.planning;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 流式执行步骤事件
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanStepEvent {

    /** 计划 ID */
    private String planId;

    /** 当前子任务 ID */
    private String subTaskId;

    /** 子任务描述 */
    private String description;

    /** 执行状态 */
    private SubTaskStatus status;

    /** 执行结果 */
    private String result;

    /** 错误信息 */
    private String error;
}