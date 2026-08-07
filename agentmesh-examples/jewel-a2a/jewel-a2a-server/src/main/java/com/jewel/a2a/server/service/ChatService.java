package com.jewel.a2a.server.service;

import com.agentmesh.core.agent.AgentConfig;
import com.agentmesh.core.agent.AgentOrchestrator;
import com.agentmesh.core.agent.ExecutionMode;
import com.agentmesh.core.agent.OrchestrationResult;
import com.agentmesh.core.agent.ReActAgent;
import com.agentmesh.core.agent.SimpleOrchestrationPlan;
import com.agentmesh.core.llm.LlmClient;
import com.agentmesh.core.llm.StreamEvent;
import com.agentmesh.core.memory.MemoryManager;
import com.agentmesh.core.prompt.PromptTemplateEngine;
import com.agentmesh.core.protocol.ChatRequest;
import com.agentmesh.core.routing.RankedAgent;
import com.agentmesh.core.routing.RoutingStrategy;
import com.agentmesh.core.session.ChatMessage;
import com.agentmesh.core.session.ConversationStore;
import com.agentmesh.core.task.Task;
import com.agentmesh.core.task.TaskRepository;
import com.agentmesh.core.task.TaskStatus;
import com.jewel.a2a.common.dto.ChatResponse;
import com.jewel.a2a.common.dto.TaskEvent;
import com.jewel.a2a.repository.entity.ConversationEntity;
import com.jewel.a2a.repository.mapper.ConversationMapper;
import com.jewel.a2a.server.config.OrchestratorRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 聊天服务：会话管理 + AgentMesh ReActAgent 调度 + 异步任务
 * <p>
 * 支持两种模式：
 * - 单 Agent 模式（/a2a/chat）：ReActAgent 兜底，通用对话
 * - 串联流水线模式（/a2a/chat/sequential）：设计师 → 工艺师 → 审核员
 */
@Slf4j
@Service
public class ChatService {

    private final ConversationStore conversationStore;
    private final TaskRepository taskRepository;
    private final ReActAgent reActAgent;
    private final AgentMeshConversationStore persistenceStore;
    private final PromptTemplateEngine promptEngine;
    private final MemoryManager memoryManager;
    private final AgentConfig designerAgent;
    private final AgentConfig crafterAgent;
    private final AgentConfig auditorAgent;

    // Phase 1 新增
    private final LlmClient llmClient;
    private final SseEmitterService sseEmitterService;

    // Phase 2 新增：AgentMesh 路由策略
    private final RoutingStrategy routingStrategy;

    // Phase 3：通过 OrchestratorRegistry 统一管理所有编排器
    private final OrchestratorRegistry orchestratorRegistry;

    // 会话持久化
    private final ConversationMapper conversationMapper;

    /** ReActAgent 单次推理的 Token 上限 */
    private static final int MAX_HISTORY_TOKENS = 4000;

    /**
     * 从 toolCalls 列表中提取 imageUrl（用于前端图片回显）
     */
    @SuppressWarnings("unchecked")
    private static String extractImageUrl(List<Map<String, Object>> toolCalls) {
        if (toolCalls == null) return null;
        for (Map<String, Object> tc : toolCalls) {
            Object result = tc.get("result");
            if (result instanceof Map<?, ?> m && m.containsKey("imageUrl")) {
                return (String) m.get("imageUrl");
            }
        }
        return null;
    }

    /**
     * 从 intermediateResults 中递归提取 imageUrl
     */
    @SuppressWarnings("unchecked")
    private static String extractImageUrlFromMap(Map<String, Object> map) {
        if (map == null) return null;
        // 直接查 imageUrl
        if (map.containsKey("imageUrl")) {
            return (String) map.get("imageUrl");
        }
        // 递归查嵌套 Map
        for (Object value : map.values()) {
            if (value instanceof Map<?, ?> m) {
                String found = extractImageUrlFromMap((Map<String, Object>) m);
                if (found != null) return found;
            }
        }
        return null;
    }

    public ChatService(ConversationStore conversationStore,
                       TaskRepository taskRepository,
                       ReActAgent reActAgent,
                       AgentMeshConversationStore persistenceStore,
                       PromptTemplateEngine promptEngine,
                       MemoryManager memoryManager,
                       @Qualifier("designerAgent") AgentConfig designerAgent,
                       @Qualifier("crafterAgent") AgentConfig crafterAgent,
                       @Qualifier("auditorAgent") AgentConfig auditorAgent,
                       LlmClient llmClient,
                       SseEmitterService sseEmitterService,
                       RoutingStrategy routingStrategy,
                       OrchestratorRegistry orchestratorRegistry,
                       ConversationMapper conversationMapper) {
        this.conversationStore = conversationStore;
        this.taskRepository = taskRepository;
        this.reActAgent = reActAgent;
        this.persistenceStore = persistenceStore;
        this.promptEngine = promptEngine;
        this.memoryManager = memoryManager;
        this.designerAgent = designerAgent;
        this.crafterAgent = crafterAgent;
        this.auditorAgent = auditorAgent;
        this.llmClient = llmClient;
        this.sseEmitterService = sseEmitterService;
        this.routingStrategy = routingStrategy;
        this.orchestratorRegistry = orchestratorRegistry;
        this.conversationMapper = conversationMapper;
    }

    /**
     * 提交聊天任务（单 Agent 模式），秒级返回
     */
    public ChatResponse submitChat(ChatRequest request) {
        String sessionId = request.getSessionId() != null
                ? request.getSessionId()
                : "chat_" + UUID.randomUUID().toString().substring(0, 8);
        String taskId = "task_" + UUID.randomUUID().toString().substring(0, 8);

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("sessionId", sessionId);
        input.put("message", request.getMessage());

        Task task = new Task(taskId, "chat", input);
        taskRepository.save(task);

        log.info("[ChatService] 聊天任务已提交: sessionId={}, taskId={}", sessionId, taskId);

        executeChat(taskId, sessionId, request.getMessage());

        return ChatResponse.builder()
                .sessionId(sessionId)
                .taskId(taskId)
                .status("PENDING")
                .message("任务已提交")
                .build();
    }

    /**
     * 提交路由聊天任务（关键词路由到对应 Agent），秒级返回
     * <p>
     * 根据用户输入的关键词自动路由到最匹配的 Agent，无匹配时回退到通用 ReActAgent。
     */
    public ChatResponse submitChatRouted(ChatRequest request) {
        String sessionId = request.getSessionId() != null
                ? request.getSessionId()
                : "route_" + UUID.randomUUID().toString().substring(0, 8);
        String taskId = "task_" + UUID.randomUUID().toString().substring(0, 8);

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("sessionId", sessionId);
        input.put("message", request.getMessage());

        Task task = new Task(taskId, "chat_routed", input);
        taskRepository.save(task);

        log.info("[ChatService] 路由聊天任务已提交: sessionId={}, taskId={}", sessionId, taskId);

        executeRouted(taskId, sessionId, request.getMessage());

        return ChatResponse.builder()
                .sessionId(sessionId)
                .taskId(taskId)
                .status("PENDING")
                .message("路由任务已提交")
                .build();
    }

    /**
     * 提交串联流水线任务（设计师 → 工艺师 → 审核员），秒级返回
     */
    public ChatResponse submitChatSequential(ChatRequest request) {
        String sessionId = request.getSessionId() != null
                ? request.getSessionId()
                : "seq_" + UUID.randomUUID().toString().substring(0, 8);
        String taskId = "task_" + UUID.randomUUID().toString().substring(0, 8);

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("sessionId", sessionId);
        input.put("message", request.getMessage());

        Task task = new Task(taskId, "chat_sequential", input);
        taskRepository.save(task);

        log.info("[ChatService] 串联流水线任务已提交: sessionId={}, taskId={}", sessionId, taskId);

        executeSequential(taskId, sessionId, request.getMessage());

        return ChatResponse.builder()
                .sessionId(sessionId)
                .taskId(taskId)
                .status("PENDING")
                .message("串联流水线任务已提交")
                .build();
    }

    @Async
    public void executeChat(String taskId, String sessionId, String message) {
        try {
            taskRepository.updateStatus(taskId, TaskStatus.RUNNING, null);

            // 使用 MemoryManager 召回历史（自动滑动窗口 + 摘要压缩）
            List<ChatMessage> history = memoryManager.recall(sessionId, MAX_HISTORY_TOKENS);

            String systemPrompt = promptEngine.render("default", null);
            ReActAgent.AgentResult agentResult = reActAgent.run(systemPrompt, message, history);

            // 使用 MemoryManager 追加消息（自动触发摘要压缩）
            memoryManager.append(sessionId,
                    ChatMessage.builder().role("user").content(message).build());
            memoryManager.append(sessionId,
                    ChatMessage.builder().role("assistant").content(agentResult.reply).build());

            persistenceStore.persist(sessionId);

            Map<String, Object> output = new LinkedHashMap<>();
            output.put("sessionId", sessionId);
            output.put("reply", agentResult.reply);
            output.put("toolCalls", agentResult.toolCalls);
            output.put("mode", "single");

            // 提取图片 URL 用于前端回显
            String imageUrl = extractImageUrl(agentResult.toolCalls);
            if (imageUrl != null) {
                output.put("imageUrl", imageUrl);
            }

            taskRepository.updateStatus(taskId, TaskStatus.SUCCESS, output);
            log.info("[ChatService] 聊天完成: sessionId={}, taskId={}, hasImage={}", sessionId, taskId, imageUrl != null);

        } catch (Exception e) {
            log.error("[ChatService] 聊天失败: sessionId={}", sessionId, e);
            taskRepository.updateStatus(taskId, TaskStatus.FAILED,
                    Map.of("error", e.getMessage()));
        }
    }

    /**
     * 串联流水线执行：设计师 → 工艺师 → 审核员
     * <p>
     * 每个 Agent 的输出作为下一个 Agent 的输入。
     * 最终输出为审核员的综合评审结果。
     */
    @Async
    public void executeSequential(String taskId, String sessionId, String message) {
        try {
            taskRepository.updateStatus(taskId, TaskStatus.RUNNING, null);

            log.info("[ChatService] 串联流水线开始: sessionId={}, taskId={}", sessionId, taskId);

            // 构建编排计划：设计师 → 工艺师 → 审核员
            SimpleOrchestrationPlan plan = SimpleOrchestrationPlan.builder()
                    .mode(ExecutionMode.SEQUENTIAL)
                    .addAgent(designerAgent)
                    .addAgent(crafterAgent)
                    .addAgent(auditorAgent)
                    .build();

            // 通过 OrchestratorRegistry 动态获取编排器
            AgentOrchestrator orchestrator = orchestratorRegistry.get(ExecutionMode.SEQUENTIAL);
            OrchestrationResult result = orchestrator.orchestrate(plan, message);

            // 保存消息（使用 MemoryManager 自动触发摘要压缩）
            memoryManager.append(sessionId,
                    ChatMessage.builder().role("user").content(message).build());
            memoryManager.append(sessionId,
                    ChatMessage.builder().role("assistant").content(result.getFinalOutput()).build());

            persistenceStore.persist(sessionId);

            Map<String, Object> output = new LinkedHashMap<>();
            output.put("sessionId", sessionId);
            output.put("reply", result.getFinalOutput());
            output.put("mode", "sequential");
            output.put("success", result.isSuccess());

            // 从 intermediateResults 提取图片 URL
            String imageUrl = extractImageUrlFromMap(result.getIntermediateResults());
            if (imageUrl != null) {
                output.put("imageUrl", imageUrl);
            }

            taskRepository.updateStatus(taskId, TaskStatus.SUCCESS, output);
            log.info("[ChatService] 串联流水线完成: sessionId={}, taskId={}, hasImage={}", sessionId, taskId, imageUrl != null);

        } catch (Exception e) {
            log.error("[ChatService] 串联流水线失败: sessionId={}", sessionId, e);
            taskRepository.updateStatus(taskId, TaskStatus.FAILED,
                    Map.of("error", e.getMessage()));
        }
    }

    /**
     * 路由执行：使用 AgentMesh RoutingStrategy 进行路由匹配
     * <p>
     * 无匹配时回退到通用 ReActAgent（default prompt）。
     */
    @Async
    public void executeRouted(String taskId, String sessionId, String message) {
        try {
            taskRepository.updateStatus(taskId, TaskStatus.RUNNING, null);

            // Phase 2：使用 AgentMesh 路由策略
            List<AgentConfig> candidates = List.of(designerAgent, crafterAgent, auditorAgent);
            List<RankedAgent> matches = routingStrategy.route(message, candidates);

            RankedAgent bestMatch = matches.isEmpty() ? null : matches.get(0);
            String agentId = bestMatch != null ? bestMatch.getAgent().getAgentId() : "default";

            log.info("[ChatService] AgentMesh路由: agentId={}, score={}, strategy={}, candidates={}",
                    agentId,
                    bestMatch != null ? bestMatch.getScore() : 0,
                    routingStrategy.getClass().getSimpleName(),
                    matches.size());

            // 召回历史
            List<ChatMessage> history = memoryManager.recall(sessionId, MAX_HISTORY_TOKENS);

            // 渲染 Agent 专属 prompt
            String promptTemplate = bestMatch != null ? bestMatch.getAgent().getPromptTemplate() : "default";
            String systemPrompt = promptEngine.render(promptTemplate, null);

            // 创建 ReActAgent 执行
            ReActAgent agent = bestMatch != null
                    ? new ReActAgent(bestMatch.getAgent().getLlmClient(),
                            bestMatch.getAgent().getToolRegistry(),
                            bestMatch.getAgent().getMaxLoops())
                    : reActAgent;
            ReActAgent.AgentResult agentResult = agent.run(systemPrompt, message, history);

            // 保存消息
            memoryManager.append(sessionId,
                    ChatMessage.builder().role("user").content(message).build());
            memoryManager.append(sessionId,
                    ChatMessage.builder().role("assistant").content(agentResult.reply).build());

            persistenceStore.persist(sessionId);

            Map<String, Object> output = new LinkedHashMap<>();
            output.put("sessionId", sessionId);
            output.put("reply", agentResult.reply);
            output.put("toolCalls", agentResult.toolCalls);
            output.put("mode", "routed");
            output.put("routedAgent", agentId);
            output.put("routingStrategy", routingStrategy.getClass().getSimpleName());

            // 提取图片 URL 用于前端回显
            String imageUrl = extractImageUrl(agentResult.toolCalls);
            if (imageUrl != null) {
                output.put("imageUrl", imageUrl);
            }

            taskRepository.updateStatus(taskId, TaskStatus.SUCCESS, output);
            log.info("[ChatService] 路由执行完成: agentId={}, sessionId={}, taskId={}, hasImage={}", agentId, sessionId, taskId, imageUrl != null);

        } catch (Exception e) {
            log.error("[ChatService] 路由执行失败: sessionId={}", sessionId, e);
            taskRepository.updateStatus(taskId, TaskStatus.FAILED,
                    Map.of("error", e.getMessage()));
        }
    }

    /**
     * 获取会话历史（兼容旧接口，返回 Jewel-A2A ChatMessage 格式）
     */
    public List<com.jewel.a2a.common.dto.ChatMessage> getHistory(String sessionId) {
        List<ChatMessage> history = memoryManager.recall(sessionId, MAX_HISTORY_TOKENS);
        return history.stream()
                .map(m -> com.jewel.a2a.common.dto.ChatMessage.builder()
                        .role(m.getRole())
                        .content(m.getContent())
                        .toolName(m.getToolName())
                        .build())
                .collect(Collectors.toList());
    }

    // ========== Phase 1 新增：流式聊天 ==========

    /**
     * 提交流式聊天任务，秒级返回
     */
    public ChatResponse submitChatStream(ChatRequest request) {
        String sessionId = request.getSessionId() != null
                ? request.getSessionId()
                : "stream_" + UUID.randomUUID().toString().substring(0, 8);
        String taskId = "task_" + UUID.randomUUID().toString().substring(0, 8);

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("sessionId", sessionId);
        input.put("message", request.getMessage());

        Task task = new Task(taskId, "chat_stream", input);
        taskRepository.save(task);

        log.info("[ChatService] 流式聊天任务已提交: sessionId={}, taskId={}", sessionId, taskId);

        executeChatStream(taskId, sessionId, request.getMessage());

        return ChatResponse.builder()
                .sessionId(sessionId)
                .taskId(taskId)
                .status("PENDING")
                .message("流式任务已提交")
                .build();
    }

    /**
     * 流式执行：使用 ReActAgent.runStream() 支持工具调用。
     * runStream() 返回 Flux<StreamEvent>，包含完整的工具调用生命周期：
     * TOOL_CALL_START → TOOL_CALL_ARGS → TOOL_CALL_END → TOOL_RESULT → 下一轮 TEXT
     */
    @Async
    public void executeChatStream(String taskId, String sessionId, String message) {
        try {
            taskRepository.updateStatus(taskId, TaskStatus.RUNNING, null);

            List<ChatMessage> history = memoryManager.recall(sessionId, MAX_HISTORY_TOKENS);
            String systemPrompt = promptEngine.render("default", null);

            // 发送开始事件
            sseEmitterService.sendEvent(taskId, TaskEvent.builder()
                    .taskId(taskId)
                    .status(com.jewel.a2a.common.enums.TaskStatus.RUNNING)
                    .message("开始生成...")
                    .build());

            // 收集完整回复（仅 TEXT 事件内容）
            StringBuilder fullReply = new StringBuilder();

            // 使用 ReActAgent.runStream() 替代 llmClient.chatStream()
            // runStream() 支持：工具调用 → 工具执行 → 继续推理 → 最终文本
            reActAgent.runStream(systemPrompt, message, history)
                    .doOnNext(event -> {
                        switch (event.getType()) {
                            case TEXT -> {
                                fullReply.append(event.getContent());
                                sseEmitterService.sendEvent(taskId, TaskEvent.builder()
                                        .taskId(taskId)
                                        .status(com.jewel.a2a.common.enums.TaskStatus.RUNNING)
                                        .message(event.getContent())
                                        .build());
                            }
                            case THINKING -> {
                                sseEmitterService.sendEvent(taskId, TaskEvent.builder()
                                        .taskId(taskId)
                                        .status(com.jewel.a2a.common.enums.TaskStatus.RUNNING)
                                        .message("[思考] " + event.getContent())
                                        .build());
                            }
                            case TOOL_CALL_START -> {
                                sseEmitterService.sendEvent(taskId, TaskEvent.builder()
                                        .taskId(taskId)
                                        .status(com.jewel.a2a.common.enums.TaskStatus.RUNNING)
                                        .message("[工具调用] " + event.getToolName() + " ...")
                                        .build());
                            }
                            case TOOL_RESULT -> {
                                log.info("[ChatService] 工具执行完成: tool={}, taskId={}",
                                        event.getToolName(), taskId);
                            }
                            case ERROR -> {
                                log.error("[ChatService] 流式推理错误: {}", event.getContent());
                            }
                            // DONE 事件在 doOnComplete 中处理
                        }
                    })
                    .doOnComplete(() -> {
                        log.info("[ChatService] 流式输出完成: taskId={}, length={}",
                                taskId, fullReply.length());

                        // 保存消息
                        memoryManager.append(sessionId,
                                ChatMessage.builder().role("user").content(message).build());
                        memoryManager.append(sessionId,
                                ChatMessage.builder().role("assistant").content(fullReply.toString()).build());
                        persistenceStore.persist(sessionId);

                        Map<String, Object> output = new LinkedHashMap<>();
                        output.put("sessionId", sessionId);
                        output.put("reply", fullReply.toString());
                        output.put("mode", "stream");

                        taskRepository.updateStatus(taskId, TaskStatus.SUCCESS, output);

                        sseEmitterService.complete(taskId, TaskEvent.builder()
                                .taskId(taskId)
                                .status(com.jewel.a2a.common.enums.TaskStatus.SUCCESS)
                                .output(output)
                                .build());
                    })
                    .doOnError(e -> {
                        log.error("[ChatService] 流式输出错误: taskId={}", taskId, e);
                        taskRepository.updateStatus(taskId, TaskStatus.FAILED,
                                Map.of("error", e.getMessage()));
                        sseEmitterService.completeWithError(taskId, e);
                    })
                    .subscribe();

        } catch (Exception e) {
            log.error("[ChatService] 流式聊天失败: sessionId={}", sessionId, e);
            taskRepository.updateStatus(taskId, TaskStatus.FAILED,
                    Map.of("error", e.getMessage()));
            sseEmitterService.completeWithError(taskId, e);
        }
    }

    // ========== Phase 3：通用编排方法 ==========

    /**
     * 条件编排：POST /a2a/chat/conditional
     */
    public ChatResponse submitChatConditional(ChatRequest request) {
        return submitOrchestrated(request, ExecutionMode.CONDITIONAL);
    }

    /**
     * 并行编排：POST /a2a/chat/parallel
     */
    public ChatResponse submitChatParallel(ChatRequest request) {
        return submitOrchestrated(request, ExecutionMode.PARALLEL);
    }

    /**
     * 辩论编排：POST /a2a/chat/debate
     */
    public ChatResponse submitChatDebate(ChatRequest request) {
        return submitOrchestrated(request, ExecutionMode.DEBATE);
    }

    /**
     * 监督编排：POST /a2a/chat/supervised
     */
    public ChatResponse submitChatSupervised(ChatRequest request) {
        return submitOrchestrated(request, ExecutionMode.SUPERVISED);
    }

    /**
     * 群体编排：POST /a2a/chat/swarm
     */
    public ChatResponse submitChatSwarm(ChatRequest request) {
        return submitOrchestrated(request, ExecutionMode.SWARM);
    }

    /**
     * 通用编排任务提交
     */
    private ChatResponse submitOrchestrated(ChatRequest request, ExecutionMode mode) {
        String sessionId = request.getSessionId() != null
                ? request.getSessionId()
                : mode.name().toLowerCase() + "_" + UUID.randomUUID().toString().substring(0, 8);
        String taskId = "task_" + UUID.randomUUID().toString().substring(0, 8);

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("sessionId", sessionId);
        input.put("message", request.getMessage());
        input.put("mode", mode.name().toLowerCase());

        Task task = new Task(taskId, "chat_" + mode.name().toLowerCase(), input);
        taskRepository.save(task);

        log.info("[ChatService] {}编排任务已提交: sessionId={}, taskId={}", mode, sessionId, taskId);

        executeOrchestrated(taskId, sessionId, request.getMessage(), mode);

        return ChatResponse.builder()
                .sessionId(sessionId)
                .taskId(taskId)
                .status("PENDING")
                .message(mode.name().toLowerCase() + "编排任务已提交")
                .build();
    }

    /**
     * 通用编排执行：通过 OrchestratorRegistry 动态获取编排器
     */
    @Async
    public void executeOrchestrated(String taskId, String sessionId, String message,
                                     ExecutionMode mode) {
        try {
            taskRepository.updateStatus(taskId, TaskStatus.RUNNING, null);
            log.info("[ChatService] {}编排开始: sessionId={}, taskId={}", mode, sessionId, taskId);

            SimpleOrchestrationPlan plan = SimpleOrchestrationPlan.builder()
                    .mode(mode)
                    .addAgent(designerAgent)
                    .addAgent(crafterAgent)
                    .addAgent(auditorAgent)
                    .build();

            AgentOrchestrator orchestrator = orchestratorRegistry.get(mode);
            OrchestrationResult result = orchestrator.orchestrate(plan, message);

            memoryManager.append(sessionId,
                    ChatMessage.builder().role("user").content(message).build());
            memoryManager.append(sessionId,
                    ChatMessage.builder().role("assistant").content(result.getFinalOutput()).build());
            persistenceStore.persist(sessionId);

            Map<String, Object> output = new LinkedHashMap<>();
            output.put("sessionId", sessionId);
            output.put("reply", result.getFinalOutput());
            output.put("mode", mode.name().toLowerCase());
            output.put("success", result.isSuccess());

            // 从 intermediateResults 提取图片 URL
            String imageUrl = extractImageUrlFromMap(result.getIntermediateResults());
            if (imageUrl != null) {
                output.put("imageUrl", imageUrl);
            }

            taskRepository.updateStatus(taskId, TaskStatus.SUCCESS, output);
            log.info("[ChatService] {}编排完成: sessionId={}, taskId={}, hasImage={}", mode, sessionId, taskId, imageUrl != null);

        } catch (Exception e) {
            log.error("[ChatService] {}编排失败: sessionId={}", mode, sessionId, e);
            taskRepository.updateStatus(taskId, TaskStatus.FAILED,
                    Map.of("error", e.getMessage(), "mode", mode.name().toLowerCase()));
        }
    }

    // ========== 会话列表管理 ==========

    /**
     * 列出所有会话（按更新时间倒序）
     */
    public List<Map<String, Object>> listConversations() {
        List<ConversationEntity> entities = conversationMapper.selectList(null);
        if (entities == null) return List.of();
        return entities.stream()
                .sorted((a, b) -> b.getUpdatedAt().compareTo(a.getUpdatedAt()))
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("sessionId", e.getSessionId());
                    m.put("updatedAt", e.getUpdatedAt() != null ? e.getUpdatedAt().toString() : "");
                    m.put("createdAt", e.getCreatedAt() != null ? e.getCreatedAt().toString() : "");
                    return m;
                })
                .collect(Collectors.toList());
    }

    /**
     * 删除会话
     */
    public boolean deleteConversation(String sessionId) {
        ConversationEntity exist = conversationMapper.findBySessionId(sessionId);
        if (exist == null) return false;
        conversationMapper.deleteById(exist.getId());
        memoryManager.clear(sessionId);
        persistenceStore.clear(sessionId);
        return true;
    }
}