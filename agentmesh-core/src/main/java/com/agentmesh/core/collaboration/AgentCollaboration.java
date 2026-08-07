package com.agentmesh.core.collaboration;

import com.agentmesh.core.agent.AgentConfig;
import com.agentmesh.core.llm.StreamEvent;
import reactor.core.publisher.Flux;
import java.util.List;

/**
 * Agent 协作模式接口。
 * 每种协作模式（Supervisor/Worker、Debate、Swarm 等）实现此接口。
 */
public interface AgentCollaboration {

    /**
     * 执行协作。
     * @param agents 参与协作的 Agent 配置列表
     * @param input 用户输入
     * @param sharedContext 共享上下文
     * @param messageBus 消息总线
     * @param collaborationId 协作流程 ID（用于全链路追踪）
     * @return 协作过程的 SSE 事件流
     */
    Flux<StreamEvent> collaborate(List<AgentConfig> agents,
                                   String input,
                                   SharedContext sharedContext,
                                   MessageBus messageBus,
                                   String collaborationId);
}
