package com.jewel.a2a.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.jewel.a2a.common.enums.TaskStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * SSE 推送事件结构
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TaskEvent {
    private String taskId;
    private TaskStatus status;
    private String message;
    private Object output;
}