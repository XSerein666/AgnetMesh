package com.agentmesh.core.planning;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 子任务
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubTask {

    /** 子任务 ID */
    private String id;

    /** 子任务描述 */
    private String description;

    /** 依赖的前置任务 ID 列表 */
    private List<String> dependsOn;

    /** 建议使用的工具名（可选） */
    private String suggestedTool;

    /** 执行状态 */
    @Builder.Default
    private SubTaskStatus status = SubTaskStatus.PENDING;

    /** 执行结果 */
    private String result;
}