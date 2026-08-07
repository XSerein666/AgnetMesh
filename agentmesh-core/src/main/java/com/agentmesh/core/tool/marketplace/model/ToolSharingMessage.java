package com.agentmesh.core.tool.marketplace.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;

/**
 * 工具共享消息协议。
 * 通过 MessageBus 在 Agent 之间传递工具发布/更新/下架事件。
 * 消息中只包含工具摘要信息，避免广播消息体积过大。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolSharingMessage {

    /** 消息 ID */
    private String messageId;

    /** 事件类型 */
    private SharingEventType eventType;

    /** 工具唯一 ID */
    private String toolId;

    /** 工具名称 */
    private String name;

    /** 工具描述 */
    private String description;

    /** 工具分类 */
    private String category;

    /** 版本号 */
    private ToolVersion version;

    /** 发布者 Agent ID */
    private String publisher;

    /** 发布者 Agent 的调用端点（消费者安装时使用） */
    private String publisherEndpointUrl;

    /** 事件时间戳 */
    @Builder.Default
    private Instant timestamp = Instant.now();

    public enum SharingEventType {
        /** 新工具发布 */
        TOOL_PUBLISHED,
        /** 工具更新（新版本） */
        TOOL_UPDATED,
        /** 工具下架 */
        TOOL_DEPRECATED,
        /** 工具卸载通知 */
        TOOL_UNPUBLISHED
    }
}
