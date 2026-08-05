package com.agentmesh.core.task;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 任务实体
 */
public class Task {

    private String taskId;
    private String skillId;
    private Map<String, Object> input;
    private TaskStatus status;
    private Object output;
    private int version; // 乐观锁版本号
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Task() {}

    public Task(String taskId, String skillId, Map<String, Object> input) {
        this.taskId = taskId;
        this.skillId = skillId;
        this.input = input;
        this.status = TaskStatus.PENDING;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    public String getSkillId() { return skillId; }
    public void setSkillId(String skillId) { this.skillId = skillId; }
    public Map<String, Object> getInput() { return input; }
    public void setInput(Map<String, Object> input) { this.input = input; }
    public TaskStatus getStatus() { return status; }
    public void setStatus(TaskStatus status) { this.status = status; }
    public Object getOutput() { return output; }
    public void setOutput(Object output) { this.output = output; }
    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}