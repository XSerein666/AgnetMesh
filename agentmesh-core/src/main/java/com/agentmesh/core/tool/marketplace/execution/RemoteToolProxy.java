package com.agentmesh.core.tool.marketplace.execution;

import com.agentmesh.core.tool.Tool;
import com.agentmesh.core.tool.marketplace.model.ToolExecutionDescriptor;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * 远程工具 RPC 代理。
 * 消费者安装远程工具时，此代理作为 Tool 接口的实现注册到本地 ToolRegistry。
 * 调用时通过 ToolExecutionBridge 透明路由到发布者 Agent。
 */
@Slf4j
public class RemoteToolProxy implements Tool<Map<String, Object>, Object> {

    private final String toolId;
    private final String name;
    private final String description;
    private final Map<String, Object> inputSchema;
    private final ToolExecutionDescriptor executionDescriptor;
    private final ToolExecutionBridge executionBridge;

    public RemoteToolProxy(String toolId, String name, String description,
                            Map<String, Object> inputSchema,
                            ToolExecutionDescriptor executionDescriptor,
                            ToolExecutionBridge executionBridge) {
        this.toolId = toolId;
        this.name = name;
        this.description = description;
        this.inputSchema = inputSchema;
        this.executionDescriptor = executionDescriptor;
        this.executionBridge = executionBridge;
    }

    @Override
    public String getId() {
        return toolId;
    }

    public String getName() {
        return name;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public Map<String, Object> getInputSchema() {
        return inputSchema;
    }

    @Override
    public Object execute(Map<String, Object> input) {
        log.debug("[RemoteToolProxy] 远程调用工具: {} → {}", toolId, executionDescriptor.getRemoteEndpointUrl());
        return executionBridge.execute(executionDescriptor, input);
    }
}
