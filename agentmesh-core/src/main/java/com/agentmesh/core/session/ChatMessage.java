package com.agentmesh.core.session;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 会话消息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {
    private String role;     // user / assistant / tool
    private String content;
    private String toolName; // 仅 tool 角色有值
}
