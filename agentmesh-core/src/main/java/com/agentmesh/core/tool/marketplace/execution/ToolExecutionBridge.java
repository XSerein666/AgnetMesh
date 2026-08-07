package com.agentmesh.core.tool.marketplace.execution;

import com.agentmesh.core.tool.Tool;
import com.agentmesh.core.tool.marketplace.model.ToolExecutionDescriptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;

import java.util.Map;

/**
 * 工具执行桥接器。
 * 根据 ToolExecutionDescriptor 的类型路由到不同的执行方式。
 */
@Slf4j
public class ToolExecutionBridge {

    private final ApplicationContext applicationContext;
    private final RemoteToolExecutor remoteToolExecutor;
    private final McpToolExecutor mcpToolExecutor;

    public ToolExecutionBridge(ApplicationContext applicationContext,
                                RemoteToolExecutor remoteToolExecutor,
                                McpToolExecutor mcpToolExecutor) {
        this.applicationContext = applicationContext;
        this.remoteToolExecutor = remoteToolExecutor;
        this.mcpToolExecutor = mcpToolExecutor;
    }

    /**
     * 根据执行描述符执行工具。
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Object execute(ToolExecutionDescriptor descriptor, Map<String, Object> input) {
        return switch (descriptor.getType()) {
            case LOCAL_BEAN -> executeLocal(descriptor, input);
            case REMOTE_RPC -> executeRemote(descriptor, input);
            case MCP_ENDPOINT -> executeMcp(descriptor, input);
        };
    }

    private Object executeLocal(ToolExecutionDescriptor descriptor, Map<String, Object> input) {
        Tool tool = (Tool) applicationContext.getBean(descriptor.getBeanName());
        return tool.execute(input);
    }

    private Object executeRemote(ToolExecutionDescriptor descriptor, Map<String, Object> input) {
        return remoteToolExecutor.execute(
                descriptor.getRemoteEndpointUrl(),
                input,
                descriptor.getTimeoutMillis(),
                descriptor.getRetryCount()
        );
    }

    private Object executeMcp(ToolExecutionDescriptor descriptor, Map<String, Object> input) {
        return mcpToolExecutor.execute(
                descriptor.getMcpServerUrl(),
                descriptor.getMcpToolName(),
                input
        );
    }
}
