package com.jewel.a2a.repository.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.jewel.a2a.repository.handler.JsonbTypeHandler;
import com.jewel.a2a.common.enums.TaskStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 任务持久化实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "a2a_task", autoResultMap = true)
public class TaskEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String taskId;

    private String skillId;

    @TableField(typeHandler = JsonbTypeHandler.class)
    private Map<String, Object> input;

    private TaskStatus status;

    @TableField(typeHandler = JsonbTypeHandler.class)
    private Object output;

    private String errorMessage;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
