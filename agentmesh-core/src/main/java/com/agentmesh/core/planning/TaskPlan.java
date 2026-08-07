package com.agentmesh.core.planning;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 任务计划
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskPlan {

    /** 计划 ID */
    private String planId;

    /** 原始目标 */
    private String goal;

    /** 子任务列表 */
    private List<SubTask> subTasks;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
