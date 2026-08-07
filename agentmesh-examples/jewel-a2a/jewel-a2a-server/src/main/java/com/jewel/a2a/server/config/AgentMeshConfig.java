package com.jewel.a2a.server.config;

import com.agentmesh.core.agent.ReActAgent;
import com.agentmesh.core.llm.LlmClient;
import com.agentmesh.core.tool.Tool;
import com.agentmesh.core.tool.ToolRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * AgentMesh 框架集成配置
 * <p>
 * 将 AgentMesh 的核心组件注入 Spring 容器，供 Jewel-A2A 业务层使用。
 * <p>
 * LlmClient 由 LlmAutoConfiguration 自动配置（application.yml 中 agentmesh.llm.*），
 * 无需手动创建 DashScopeLlmClient。
 * ToolRegistry 由业务模块手动创建（AgentMesh 无 ToolAutoConfiguration）。
 */
@Configuration
public class AgentMeshConfig {

    /**
     * AgentMesh ToolRegistry Bean
     * <p>
     * 自动收集所有实现 {@link com.agentmesh.core.tool.Tool} 接口的 Spring Bean。
     */
    @Bean
    public ToolRegistry agentMeshToolRegistry(List<Tool<?, ?>> tools) {
        return new ToolRegistry(tools, 30);
    }

    /**
     * ReActAgent 引擎
     * <p>
     * LlmClient 由 LlmAutoConfiguration 自动注入，无需手动创建。
     */
    @Bean
    public ReActAgent reActAgent(LlmClient llmClient, ToolRegistry agentMeshToolRegistry) {
        return new ReActAgent(llmClient, agentMeshToolRegistry, 5);
    }
}