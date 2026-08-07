package com.agentmesh.core.protocol;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 聊天请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatRequest {
    /** 会话ID，可选，不传则新建会话 */
    private String sessionId;

    /** 用户消息，必填 */
    @NotBlank(message = "消息内容不能为空")
    private String message;
}
