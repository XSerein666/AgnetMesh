package com.agentmesh.core.task;

import com.agentmesh.core.agent.AgentConfig;
import com.agentmesh.core.agent.ReActAgent;
import com.agentmesh.core.agent.SequentialAgentOrchestrator;
import com.agentmesh.core.session.ChatMessage;
import com.agentmesh.core.session.ConversationStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 异步任务执行器
 */
@Slf4j
public class TaskExecutor {

    private final TaskRepository taskRepository;
    private final ConversationStore conversationStore;
    private final AgentConfig agentConfig;
    /** Agent 工厂：与编排器保持一致的创建方式。可选，未注入时回退到直接 new ReActAgent */
    private final SequentialAgentOrchestrator.ReActAgentFactory agentFactory;

    public TaskExecutor(TaskRepository taskRepository, ConversationStore conversationStore,
                        AgentConfig agentConfig) {
        this(taskRepository, conversationStore, agentConfig, null);
    }

    public TaskExecutor(TaskRepository taskRepository, ConversationStore conversationStore,
                        AgentConfig agentConfig,
                        SequentialAgentOrchestrator.ReActAgentFactory agentFactory) {
        this.taskRepository = taskRepository;
        this.conversationStore = conversationStore;
        this.agentConfig = agentConfig;
        this.agentFactory = agentFactory;
    }

    /**
     * 提交直接 Tool 调用任务
     */
    public String submitToolTask(String skillId, Map<String, Object> input) {
        String taskId = "task_" + uuid();
        Task task = new Task(taskId, skillId, input);
        taskRepository.save(task);
        return taskId;
    }

    /**
     * 提交聊天任务（异步执行）
     */
    public String submitChatTask(String sessionId, String message) {
        String taskId = "task_" + uuid();
        String sid = sessionId != null ? sessionId : "chat_" + uuid();

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("sessionId", sid);
        input.put("message", message);

        Task task = new Task(taskId, "chat", input);
        taskRepository.save(task);

        executeChatAsync(taskId, sid, message);
        return taskId;
    }

    @Async
    public void executeChatAsync(String taskId, String sessionId, String message) {
        try {
            taskRepository.updateStatus(taskId, TaskStatus.RUNNING, null);

            List<ChatMessage> history = conversationStore.getHistory(sessionId);
            ReActAgent agent = agentFactory != null
                    ? agentFactory.create(agentConfig)
                    : new ReActAgent(
                            agentConfig.getLlmClient(),
                            agentConfig.getToolRegistry(),
                            agentConfig.getMaxLoops()
                    );
            ReActAgent.AgentResult agentResult = agent.run(
                    agentConfig.getSystemPrompt(), message, history);

            conversationStore.append(sessionId,
                    ChatMessage.builder().role("user").content(message).build());
            conversationStore.append(sessionId,
                    ChatMessage.builder().role("assistant").content(agentResult.reply).build());

            Map<String, Object> output = new LinkedHashMap<>();
            output.put("sessionId", sessionId);
            output.put("reply", agentResult.reply);
            output.put("toolCalls", agentResult.toolCalls);

            taskRepository.updateStatus(taskId, TaskStatus.SUCCESS, output);
            log.info("[TaskExecutor] 聊天完成: sessionId={}, taskId={}", sessionId, taskId);

        } catch (Exception e) {
            log.error("[TaskExecutor] 聊天失败: sessionId={}", sessionId, e);
            taskRepository.updateStatus(taskId, TaskStatus.FAILED,
                    Map.of("error", e.getMessage()));
        }
    }

    private String uuid() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
