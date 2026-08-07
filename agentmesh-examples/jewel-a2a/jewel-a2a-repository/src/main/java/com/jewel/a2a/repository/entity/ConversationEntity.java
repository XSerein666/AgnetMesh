package com.jewel.a2a.repository.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.jewel.a2a.repository.handler.JsonbTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会话持久化实体
 */
@Data
@TableName("conversation")
public class ConversationEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String sessionId;

    @TableField(typeHandler = JsonbTypeHandler.class)
    private Object messages;   // JSONB，存储 ChatMessage 列表

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}