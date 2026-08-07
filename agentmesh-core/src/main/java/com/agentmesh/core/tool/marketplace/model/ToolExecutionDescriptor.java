package com.agentmesh.core.tool.marketplace.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.io.Serializable;
import java.util.Map;

/**
 * 工具执行描述符。
 * 解决 ToolMetadata 与可执行 Tool 实例之间的桥梁问题。
 * 消费者通过此描述符确定如何调用工具。
 *
 * 三种执行类型：
 * - LOCAL_BEAN：发布者本地 Bean（Spring 容器中的 Tool 实例），消费者不可见
 * - REMOTE_RPC：跨 Agent 远程调用（消费者通过 RPC 代理调用发布者工具）
 * - MCP_ENDPOINT：MCP 协议工具端点
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "工具执行描述符")
public class ToolExecutionDescriptor implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 执行类型 */
    @Schema(description = "执行类型", example = "REMOTE_RPC")
    private ExecutionType type;

    /**
     * 本地 Bean 名称（LOCAL_BEAN 类型）。
     * 指向 Spring 容器中 Tool 实例的 beanName，仅发布者自己使用。
     */
    @Schema(description = "本地 Bean 名称（LOCAL_BEAN 类型）", example = "weatherTool")
    private String beanName;

    /**
     * 远程调用端点 URL（REMOTE_RPC 类型）。
     * 格式：http://{agent-host}:{port}/api/v1/tool-marketplace/execute/{toolId}
     */
    @Schema(description = "远程调用端点 URL（REMOTE_RPC 类型）", example = "http://localhost:8081/api/v1/tool-marketplace/execute")
    private String remoteEndpointUrl;

    /**
     * MCP 服务器地址（MCP_ENDPOINT 类型）。
     */
    @Schema(description = "MCP 服务器地址（MCP_ENDPOINT 类型）")
    private String mcpServerUrl;

    /**
     * MCP 工具名称（MCP Server 中的工具标识）。
     */
    @Schema(description = "MCP 工具名称")
    private String mcpToolName;

    /** 输入序列化策略 */
    @Builder.Default
    @Schema(description = "输入序列化策略", example = "JSON")
    private SerializationStrategy inputSerialization = SerializationStrategy.JSON;

    /** 输出序列化策略 */
    @Builder.Default
    @Schema(description = "输出序列化策略", example = "JSON")
    private SerializationStrategy outputSerialization = SerializationStrategy.JSON;

    /** 超时时间（毫秒） */
    @Builder.Default
    @Schema(description = "超时时间（毫秒）", example = "30000")
    private long timeoutMillis = 30000;

    /** 重试次数（0 表示不重试） */
    @Builder.Default
    @Schema(description = "重试次数（0 表示不重试）", example = "0")
    private int retryCount = 0;

    /** 额外的连接配置（如 HTTP 头、认证信息等） */
    @Schema(description = "额外的连接配置")
    private Map<String, String> extraConfig;

    public enum ExecutionType {
        LOCAL_BEAN,
        REMOTE_RPC,
        MCP_ENDPOINT
    }

    public enum SerializationStrategy {
        JSON,
        PROTOBUF,
        MSGPACK
    }
}
