package com.agentmesh.core.agent;

import com.agentmesh.core.llm.StreamEvent;
import reactor.core.publisher.Flux;

/**
 * Agent 编排器接口。
 * 不预设编排模式，由具体实现定义协作逻辑。
 */
public interface AgentOrchestrator {

    /**
     * 同步编排入口
     * @param plan 编排计划（定义 Agent 拓扑、执行顺序、依赖关系）
     * @param input 用户输入
     * @return 编排结果
     */
    OrchestrationResult orchestrate(OrchestrationPlan plan, String input);

    /**
     * 流式编排入口。
     * 编排多个 Agent 协作处理用户输入，返回 SSE 事件流。
     * @param plan 编排计划
     * @param input 用户输入
     * @return SSE 事件流
     */
    Flux<StreamEvent> orchestrateStream(OrchestrationPlan plan, String input);
}