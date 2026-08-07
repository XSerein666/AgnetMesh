package com.jewel.a2a.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.jewel.a2a.common.enums.TaskStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * /a2a/run 响应体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TaskResponse {
    private String taskId;
    private TaskStatus status;
    private String message;
}