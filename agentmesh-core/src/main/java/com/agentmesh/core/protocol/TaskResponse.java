package com.agentmesh.core.protocol;

import com.agentmesh.core.task.TaskStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
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
