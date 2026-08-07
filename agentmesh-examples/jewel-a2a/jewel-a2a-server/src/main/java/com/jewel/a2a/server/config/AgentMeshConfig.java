package com.jewel.a2a.server.config;

import com.agentmesh.core.agent.AgentConfig;
import com.agentmesh.core.agent.AgentMeshActuator;
import com.agentmesh.core.agent.ConditionalOrchestrator;
import com.agentmesh.core.agent.DebateOrchestrator;
import com.agentmesh.core.agent.ParallelOrchestrator;
import com.agentmesh.core.agent.ReActAgent;
import com.agentmesh.core.agent.SequentialAgentOrchestrator;
import com.agentmesh.core.agent.SupervisedOrchestrator;
import com.agentmesh.core.agent.SwarmOrchestrator;
import com.agentmesh.core.collaboration.ApprovalGate;
import com.agentmesh.core.collaboration.CollaborationMetrics;
import com.agentmesh.core.collaboration.DebateCollaboration;
import com.agentmesh.core.collaboration.FileWorkflowStateStore;
import com.agentmesh.core.collaboration.InMemoryMessageBus;
import com.agentmesh.core.collaboration.MessageBus;
import com.agentmesh.core.collaboration.SharedContext;
import com.agentmesh.core.collaboration.SupervisorWorkerCollaboration;
import com.agentmesh.core.collaboration.SwarmCollaboration;
import com.agentmesh.core.collaboration.WorkflowStateStore;
import com.agentmesh.core.infrastructure.AgentMeshMetrics;
import com.agentmesh.core.llm.LlmClient;
import com.agentmesh.core.planning.DagPlanExecutor;
import com.agentmesh.core.planning.LlmTaskPlanner;
import com.agentmesh.core.planning.TaskPlanner;
import com.agentmesh.core.prompt.PromptTemplateEngine;
import com.agentmesh.core.prompt.TokenBudgetManager;
import com.agentmesh.core.remote.AgentClient;
import com.agentmesh.core.routing.RoutingStrategy;
import com.agentmesh.core.tool.Tool;
import com.agentmesh.core.tool.ToolRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.time.Duration;
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
     * Token 预算管理器
     */
    @Bean
    public TokenBudgetManager tokenBudgetManager() {
        return new TokenBudgetManager(2000);
    }

    /**
     * Prompt 模板引擎
     */
    @Bean
    public PromptTemplateEngine promptTemplateEngine(ToolRegistry agentMeshToolRegistry,
                                                      TokenBudgetManager tokenBudgetManager) {
        return new PromptTemplateEngine(agentMeshToolRegistry, tokenBudgetManager);
    }

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
     * ReActAgent 引擎（单 Agent 兜底模式）
     * <p>
     * LlmClient 由 LlmAutoConfiguration 自动注入，无需手动创建。
     */
    @Bean
    public ReActAgent reActAgent(LlmClient llmClient, ToolRegistry agentMeshToolRegistry) {
        return new ReActAgent(llmClient, agentMeshToolRegistry, 5);
    }

    /**
     * ReActAgentFactory Bean：供 AgentRegistryAutoConfiguration 中的
     * SequentialAgentOrchestrator / ConditionalOrchestrator / ParallelOrchestrator 注入。
     * <p>
     * 从 AgentConfig 创建 ReActAgent 实例。
     */
    @Bean
    public SequentialAgentOrchestrator.ReActAgentFactory reActAgentFactory() {
        return config -> new ReActAgent(config.getLlmClient(), config.getToolRegistry(), config.getMaxLoops());
    }

    /**
     * SequentialAgentOrchestrator：顺序执行多个 Agent（设计师 → 工艺师 → 审核员）
     * <p>
     * Phase 4a：注入 AgentClient 支持远程 Agent 调用。
     */
    @Bean
    public SequentialAgentOrchestrator sequentialAgentOrchestrator(
            SequentialAgentOrchestrator.ReActAgentFactory reActAgentFactory,
            AgentClient agentClient,
            AgentMeshMetrics agentMeshMetrics) {
        return new SequentialAgentOrchestrator(reActAgentFactory, agentClient, agentMeshMetrics);
    }

    // ========== 多 Agent 角色定义 ==========

    /**
     * 设计师 Agent：珠宝设计图生成
     */
    @Bean
    public AgentConfig designerAgent(LlmClient llmClient,
                                      ToolRegistry agentMeshToolRegistry,
                                      PromptTemplateEngine promptTemplateEngine) {
        return AgentConfig.builder()
                .agentId("designer")
                .role("designer")
                .promptTemplate("designer")
                .promptEngine(promptTemplateEngine)
                .llmClient(llmClient)
                .toolRegistry(agentMeshToolRegistry)
                .maxLoops(5)
                .description("珠宝设计师，根据需求生成专业级珠宝设计图")
                .routingTags(List.of("设计", "画图", "生成", "款式", "设计图", "戒指", "项链", "耳环"))
                .build();
    }

    /**
     * 工艺师 Agent：工艺可行性校验 + 知识检索
     */
    @Bean
    public AgentConfig crafterAgent(LlmClient llmClient,
                                     ToolRegistry agentMeshToolRegistry,
                                     PromptTemplateEngine promptTemplateEngine) {
        return AgentConfig.builder()
                .agentId("crafter")
                .role("crafter")
                .promptTemplate("crafter")
                .promptEngine(promptTemplateEngine)
                .llmClient(llmClient)
                .toolRegistry(agentMeshToolRegistry)
                .maxLoops(5)
                .description("珠宝工艺师，校验工艺可行性并提供知识参考")
                .routingTags(List.of("工艺", "可行性", "材质", "制作", "镶嵌", "金属", "宝石"))
                .build();
    }

    /**
     * 审核员 Agent：设计图审核 + 综合评审
     */
    @Bean
    public AgentConfig auditorAgent(LlmClient llmClient,
                                     ToolRegistry agentMeshToolRegistry,
                                     PromptTemplateEngine promptTemplateEngine) {
        return AgentConfig.builder()
                .agentId("auditor")
                .role("auditor")
                .promptTemplate("auditor")
                .promptEngine(promptTemplateEngine)
                .llmClient(llmClient)
                .toolRegistry(agentMeshToolRegistry)
                .maxLoops(3)
                .description("珠宝审核专家，综合评审设计方案和工艺报告")
                .routingTags(List.of("审核", "检查", "评估", "建议", "质量", "评审"))
                .build();
    }

    // ========== Phase 3：Metrics ==========

    /**
     * AgentMesh 核心指标（Micrometer）
     */
    @Bean
    public AgentMeshMetrics agentMeshMetrics(MeterRegistry meterRegistry) {
        return new AgentMeshMetrics(meterRegistry);
    }

    /**
     * 协作指标
     */
    @Bean
    public CollaborationMetrics collaborationMetrics(MeterRegistry meterRegistry) {
        return new CollaborationMetrics(meterRegistry);
    }

    // ========== Phase 3：协作框架 ==========

    /**
     * 共享上下文：多 Agent 间数据共享，使用 ConcurrentHashMap 实现无锁并发
     */
    @Bean
    public SharedContext sharedContext() {
        return new SharedContext("default");
    }

    /**
     * 消息总线：Agent 间结构化消息传递。
     * 开发环境使用 InMemoryMessageBus，生产环境通过 @Profile("production") 切换为 Redis/Kafka 实现。
     */
    @Bean
    @Profile("!production")
    public MessageBus messageBus(CollaborationMetrics collaborationMetrics) {
        return new InMemoryMessageBus(256, collaborationMetrics);
    }

    /**
     * 工作流状态存储：持久化审批流程状态。
     * 开发环境使用文件存储，生产环境通过 @Profile("production") 切换为数据库实现。
     */
    @Bean
    @Profile("!production")
    public WorkflowStateStore workflowStateStore() {
        return new FileWorkflowStateStore("./data/workflow-states");
    }

    /**
     * 辩论协作：两个 Agent 辩论 + 评判者仲裁
     */
    @Bean
    public DebateCollaboration debateCollaboration(AgentMeshMetrics agentMeshMetrics,
                                                    CollaborationMetrics collaborationMetrics) {
        return new DebateCollaboration(Duration.ofSeconds(120), agentMeshMetrics, collaborationMetrics);
    }

    /**
     * 监督者-工作者协作：Supervisor 拆解任务 → Worker 执行
     */
    @Bean
    public SupervisorWorkerCollaboration supervisorWorkerCollaboration(
            AgentMeshMetrics agentMeshMetrics,
            CollaborationMetrics collaborationMetrics) {
        return new SupervisorWorkerCollaboration(Duration.ofSeconds(300), agentMeshMetrics, collaborationMetrics);
    }

    /**
     * 群体协作：多个 Agent 并行执行，汇总结果
     */
    @Bean
    public SwarmCollaboration swarmCollaboration(AgentMeshMetrics agentMeshMetrics,
                                                  CollaborationMetrics collaborationMetrics) {
        return new SwarmCollaboration(Duration.ofSeconds(300), agentMeshMetrics, collaborationMetrics);
    }

    /**
     * 审批门控：Human-in-the-Loop 审批流程。
     * 通过 agentmesh.collaboration.approval.enabled 配置项控制开关（在 ApprovalGate 内部判断）。
     */
    @Bean
    public ApprovalGate approvalGate(WorkflowStateStore workflowStateStore,
                                      CollaborationMetrics collaborationMetrics) {
        return new ApprovalGate(workflowStateStore, Duration.ofMinutes(5), collaborationMetrics);
    }

    // ========== Phase 3：高级编排器（5 个） ==========

    /**
     * ConditionalOrchestrator：根据路由策略动态选择最佳 Agent。
     * Phase 4a：注入 AgentClient 支持远程 Agent 调用。
     */
    @Bean
    public ConditionalOrchestrator conditionalOrchestrator(
            SequentialAgentOrchestrator.ReActAgentFactory factory,
            AgentClient agentClient,
            RoutingStrategy routingStrategy,
            AgentMeshMetrics agentMeshMetrics) {
        return new ConditionalOrchestrator(factory, agentClient, routingStrategy, agentMeshMetrics);
    }

    /**
     * ParallelOrchestrator：并行执行所有 Agent。
     * Phase 4a：注入 AgentClient 支持远程 Agent 调用。
     */
    @Bean
    public ParallelOrchestrator parallelOrchestrator(
            SequentialAgentOrchestrator.ReActAgentFactory factory,
            AgentClient agentClient,
            AgentMeshMetrics agentMeshMetrics) {
        return new ParallelOrchestrator(factory, agentClient, agentMeshMetrics);
    }

    /**
     * DebateOrchestrator：多 Agent 辩论模式。
     * 注入 DebateCollaboration 作为 AgentCollaboration 实现。
     */
    @Bean
    public DebateOrchestrator debateOrchestrator(MessageBus messageBus,
                                                  AgentMeshMetrics agentMeshMetrics,
                                                  DebateCollaboration debateCollaboration) {
        return new DebateOrchestrator(debateCollaboration, messageBus, agentMeshMetrics,
                Duration.ofSeconds(120));
    }

    /**
     * SupervisedOrchestrator：监督者-工作者模式。
     * 注入 SupervisorWorkerCollaboration 作为 AgentCollaboration 实现。
     */
    @Bean
    public SupervisedOrchestrator supervisedOrchestrator(MessageBus messageBus,
                                                          AgentMeshMetrics agentMeshMetrics,
                                                          SupervisorWorkerCollaboration collaboration) {
        return new SupervisedOrchestrator(collaboration, messageBus, agentMeshMetrics,
                Duration.ofSeconds(300));
    }

    /**
     * SwarmOrchestrator：群体协作模式。
     * 注入 SwarmCollaboration 作为 AgentCollaboration 实现。
     */
    @Bean
    public SwarmOrchestrator swarmOrchestrator(MessageBus messageBus,
                                                AgentMeshMetrics agentMeshMetrics,
                                                SwarmCollaboration collaboration) {
        return new SwarmOrchestrator(collaboration, messageBus, agentMeshMetrics,
                Duration.ofSeconds(300));
    }

    // ========== Phase 3：Actuator ==========

    /**
     * AgentMesh Actuator 端点：提供 Prompt 模板预览和管理功能。
     */
    @Bean
    public AgentMeshActuator agentMeshActuator(PromptTemplateEngine promptTemplateEngine) {
        return new AgentMeshActuator(promptTemplateEngine);
    }

    // ========== Phase 4b：任务规划 ==========

    /**
     * LLM 任务规划器：将用户目标分解为子任务 DAG。
     */
    @Bean
    public TaskPlanner taskPlanner(LlmClient llmClient) {
        return new LlmTaskPlanner(llmClient, 5);
    }

    /**
     * DAG 拓扑执行器：按依赖顺序执行子任务。
     */
    @Bean
    public DagPlanExecutor dagPlanExecutor() {
        return new DagPlanExecutor(4, java.time.Duration.ofSeconds(60), true);
    }
}