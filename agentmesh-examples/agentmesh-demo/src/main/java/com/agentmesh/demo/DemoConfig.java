package com.agentmesh.demo;

import com.agentmesh.core.agent.AgentConfig;
import com.agentmesh.core.agent.ReActAgent;
import com.agentmesh.core.agent.SequentialAgentOrchestrator;
import com.agentmesh.core.infrastructure.AgentMeshMetrics;
import com.agentmesh.core.llm.DashScopeLlmClient;
import com.agentmesh.core.llm.LlmClient;
import com.agentmesh.core.prompt.PromptTemplateEngine;
import com.agentmesh.core.prompt.TokenBudgetManager;
import com.agentmesh.core.protocol.AgentCard;
import com.agentmesh.core.protocol.AgentSkill;
import com.agentmesh.core.registry.AgentRegistryProperties;
import com.agentmesh.core.session.ConversationStore;
import com.agentmesh.core.session.InMemoryConversationStore;
import com.agentmesh.core.task.TaskExecutor;
import com.agentmesh.core.task.TaskRepository;
import com.agentmesh.core.tool.Tool;
import com.agentmesh.core.tool.ToolRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Configuration
public class DemoConfig {

    @Value("${dashscope.api-key:}")
    private String apiKey;

    @Value("${agentmesh.tool.execute-timeout:30}")
    private long toolExecuteTimeout;

    @Bean
    public ToolRegistry toolRegistry(List<Tool<?, ?>> toolList, AgentMeshMetrics metrics) {
        return new ToolRegistry(toolList, metrics, toolExecuteTimeout);
    }

    @Bean
    public AgentCard demoAgentCard(AgentRegistryProperties properties,
                                    ToolRegistry toolRegistry) {
        AgentCard card = properties.getSelf().toAgentCard();
        List<AgentSkill> skills = toolRegistry.toDefinitions().stream()
                .map(def -> AgentSkill.builder()
                        .id(def.getName())
                        .name(def.getName())
                        .description(def.getDescription())
                        .agentId(card.getAgentId())
                        .inputSchema(def.getParameters())
                        .build())
                .collect(Collectors.toList());
        card.setSkills(skills);
        return card;
    }

    @Bean
    public LlmClient llmClient(AgentMeshMetrics metrics) {
        return new DashScopeLlmClient(apiKey, metrics);
    }

    @Bean
    public ConversationStore conversationStore() {
        return new InMemoryConversationStore();
    }

    @Bean
    public TokenBudgetManager tokenBudgetManager() {
        return new TokenBudgetManager();
    }

    @Bean
    public PromptTemplateEngine promptTemplateEngine(ToolRegistry toolRegistry, TokenBudgetManager tokenBudgetManager) {
        return new PromptTemplateEngine(toolRegistry, tokenBudgetManager);
    }

    @Bean
    public ReActAgent reActAgent(LlmClient llmClient, ToolRegistry toolRegistry,
                                  AgentMeshMetrics metrics) {
        return new ReActAgent(llmClient, toolRegistry, 5, metrics);
    }

    /**
     * ReActAgent 工厂：供 Orchestrator 创建 Agent 实例。
     * 每个 Agent 共享同一个 LlmClient 和 ToolRegistry。
     */
    @Bean
    public SequentialAgentOrchestrator.ReActAgentFactory reActAgentFactory(
            LlmClient llmClient, ToolRegistry toolRegistry, AgentMeshMetrics metrics) {
        return config -> new ReActAgent(llmClient, toolRegistry, config.getMaxLoops(), metrics);
    }

    @Bean
    public AgentConfig agentConfig(LlmClient llmClient, ToolRegistry toolRegistry,
                                    PromptTemplateEngine promptEngine) {
        return AgentConfig.builder()
                .systemPrompt("""
                        你是一个智能助手，可以调用工具来帮助用户。
                        请用中文回复。""")
                .llmClient(llmClient)
                .toolRegistry(toolRegistry)
                .promptEngine(promptEngine)
                .build();
    }

    @Bean
    public TaskRepository taskRepository() {
        return new InMemoryTaskRepository();
    }

    @Bean
    public TaskExecutor AgentMeshTaskExecutor(TaskRepository taskRepository,
                                               ConversationStore conversationStore,
                                               AgentConfig agentConfig,
                                               SequentialAgentOrchestrator.ReActAgentFactory agentFactory) {
        return new TaskExecutor(taskRepository, conversationStore, agentConfig, agentFactory);
    }

    /**
     * 内存任务存储（示例实现）
     */
    static class InMemoryTaskRepository implements TaskRepository {
        private final Map<String, com.agentmesh.core.task.Task> store = new ConcurrentHashMap<>();

        @Override
        public void save(com.agentmesh.core.task.Task task) {
            store.put(task.getTaskId(), task);
        }

        @Override
        public void updateStatus(String taskId, com.agentmesh.core.task.TaskStatus status, Object output) {
            com.agentmesh.core.task.Task task = store.get(taskId);
            if (task != null) {
                task.setStatus(status);
                task.setOutput(output);
                task.setUpdatedAt(java.time.LocalDateTime.now());
            }
        }

        @Override
        public com.agentmesh.core.task.Task findById(String taskId) {
            return store.get(taskId);
        }
    }
}