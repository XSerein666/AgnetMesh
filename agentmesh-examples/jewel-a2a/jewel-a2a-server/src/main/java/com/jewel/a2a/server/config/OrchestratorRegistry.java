package com.jewel.a2a.server.config;

import com.agentmesh.core.agent.AgentOrchestrator;
import com.agentmesh.core.agent.ConditionalOrchestrator;
import com.agentmesh.core.agent.DebateOrchestrator;
import com.agentmesh.core.agent.ExecutionMode;
import com.agentmesh.core.agent.ParallelOrchestrator;
import com.agentmesh.core.agent.SequentialAgentOrchestrator;
import com.agentmesh.core.agent.SupervisedOrchestrator;
import com.agentmesh.core.agent.SwarmOrchestrator;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

/**
 * 编排器注册中心：将 ExecutionMode 映射到对应的 AgentOrchestrator 实现。
 * <p>
 * 解决 ChatService 构造函数膨胀问题 —— 新增编排模式只需修改本类，
 * 无需修改 ChatService。构造函数注入所有编排器，编译期保证完整性。
 */
@Component
public class OrchestratorRegistry {

    private final Map<ExecutionMode, AgentOrchestrator> registry = new EnumMap<>(ExecutionMode.class);

    public OrchestratorRegistry(
            SequentialAgentOrchestrator sequential,
            ConditionalOrchestrator conditional,
            ParallelOrchestrator parallel,
            DebateOrchestrator debate,
            SupervisedOrchestrator supervised,
            SwarmOrchestrator swarm) {
        registry.put(ExecutionMode.SEQUENTIAL, sequential);
        registry.put(ExecutionMode.CONDITIONAL, conditional);
        registry.put(ExecutionMode.PARALLEL, parallel);
        registry.put(ExecutionMode.DEBATE, debate);
        registry.put(ExecutionMode.SUPERVISED, supervised);
        registry.put(ExecutionMode.SWARM, swarm);
    }

    /**
     * 根据模式获取编排器
     * @throws IllegalArgumentException 如果 mode 不被支持
     */
    public AgentOrchestrator get(ExecutionMode mode) {
        AgentOrchestrator orchestrator = registry.get(mode);
        if (orchestrator == null) {
            throw new IllegalArgumentException("Unsupported execution mode: " + mode);
        }
        return orchestrator;
    }
}