package com.jewel.a2a.server.controller;

import com.agentmesh.core.protocol.AgentCard;
import com.agentmesh.core.protocol.ChatRequest;
import com.agentmesh.core.registry.AgentRegistry;
import com.jewel.a2a.common.dto.*;
import com.jewel.a2a.common.enums.TaskStatus;
import com.jewel.a2a.repository.entity.TaskEntity;
import com.jewel.a2a.server.service.ChatService;
import com.jewel.a2a.server.service.SseEmitterService;
import com.jewel.a2a.server.service.TaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

/**
 * A2A 协议核心接口
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class AgentController {

    private final AgentRegistry agentRegistry;
    private final TaskService taskService;
    private final SseEmitterService sseEmitterService;
    private final ChatService chatService;

    /**
     * 名片接口：GET /.well-known/agent.json
     * Phase 4a：从 AgentRegistry 获取 AgentCard
     */
    @GetMapping("/.well-known/agent.json")
    public AgentCard getAgentCard() {
        var node = agentRegistry.resolve("jewel-a2a");
        if (node == null) {
            log.error("Agent 'jewel-a2a' 未在 AgentRegistry 中注册");
            throw new RuntimeException("Agent 'jewel-a2a' not registered");
        }
        return node.getCard();
    }

    /**
     * 任务接口：POST /a2a/run（精确 skillId 路由）
     */
    @PostMapping("/a2a/run")
    public TaskResponse runTask(@Valid @RequestBody TaskRequest request) {
        return taskService.submitTask(request);
    }

    /**
     * 聊天接口：POST /a2a/chat（单 Agent 模式，自然语言 + ReActAgent 编排）
     */
    @PostMapping("/a2a/chat")
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        return chatService.submitChat(request);
    }

    /**
     * 串联流水线接口：POST /a2a/chat/sequential（设计师 → 工艺师 → 审核员）
     */
    @PostMapping("/a2a/chat/sequential")
    public ChatResponse chatSequential(@Valid @RequestBody ChatRequest request) {
        return chatService.submitChatSequential(request);
    }

    /**
     * 路由聊天接口：POST /a2a/chat/routed（关键词路由到对应 Agent）
     */
    @PostMapping("/a2a/chat/routed")
    public ChatResponse chatRouted(@Valid @RequestBody ChatRequest request) {
        return chatService.submitChatRouted(request);
    }

    /**
     * 流式聊天接口：POST /a2a/chat/stream（Phase 1 新增）
     * 客户端通过 /a2a/result/{taskId} SSE 订阅流式输出
     */
    @PostMapping("/a2a/chat/stream")
    public ChatResponse chatStream(@Valid @RequestBody ChatRequest request) {
        return chatService.submitChatStream(request);
    }

    // ========== Phase 3 新增：高级编排端点 ==========

    /**
     * 条件编排：POST /a2a/chat/conditional
     * 根据路由策略动态选择最佳 Agent 执行
     */
    @PostMapping("/a2a/chat/conditional")
    public ChatResponse chatConditional(@Valid @RequestBody ChatRequest request) {
        return chatService.submitChatConditional(request);
    }

    /**
     * 并行编排：POST /a2a/chat/parallel
     * 同时启动所有 Agent 并发执行
     */
    @PostMapping("/a2a/chat/parallel")
    public ChatResponse chatParallel(@Valid @RequestBody ChatRequest request) {
        return chatService.submitChatParallel(request);
    }

    /**
     * 辩论编排：POST /a2a/chat/debate
     * 多 Agent 多轮辩论，评判者仲裁
     */
    @PostMapping("/a2a/chat/debate")
    public ChatResponse chatDebate(@Valid @RequestBody ChatRequest request) {
        return chatService.submitChatDebate(request);
    }

    /**
     * 监督编排：POST /a2a/chat/supervised
     * 主管 Agent 拆解任务并分派给 Worker
     */
    @PostMapping("/a2a/chat/supervised")
    public ChatResponse chatSupervised(@Valid @RequestBody ChatRequest request) {
        return chatService.submitChatSupervised(request);
    }

    /**
     * 群体编排：POST /a2a/chat/swarm
     * 多个 Agent 并行探索，汇总最优结果
     */
    @PostMapping("/a2a/chat/swarm")
    public ChatResponse chatSwarm(@Valid @RequestBody ChatRequest request) {
        return chatService.submitChatSwarm(request);
    }

    /**
     * 会话历史：GET /a2a/chat/{sessionId}
     */
    @GetMapping("/a2a/chat/{sessionId}")
    public Map<String, Object> getChatHistory(@PathVariable String sessionId) {
        List<ChatMessage> messages = chatService.getHistory(sessionId);
        return Map.of("sessionId", sessionId, "messages", messages);
    }

    /**
     * SSE 结果订阅：GET /a2a/result/{taskId}
     */
    @GetMapping(value = "/a2a/result/{taskId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribeResult(@PathVariable String taskId) {
        return sseEmitterService.createEmitter(taskId);
    }

    /**
     * 轮询任务状态：GET /a2a/task/{taskId}
     */
    @GetMapping("/a2a/task/{taskId}")
    public TaskEvent queryTask(@PathVariable String taskId) {
        TaskEntity entity = taskService.getTask(taskId);
        return TaskEvent.builder()
                .taskId(entity.getTaskId())
                .status(entity.getStatus())
                .message(entity.getErrorMessage())
                .output(entity.getOutput())
                .build();
    }

    // ========== 会话管理 ==========

    /**
     * 列出所有会话：GET /a2a/conversations
     */
    @GetMapping("/a2a/conversations")
    public List<Map<String, Object>> listConversations() {
        return chatService.listConversations();
    }

    /**
     * 删除会话：DELETE /a2a/conversations/{sessionId}
     */
    @DeleteMapping("/a2a/conversations/{sessionId}")
    public Map<String, Object> deleteConversation(@PathVariable String sessionId) {
        boolean deleted = chatService.deleteConversation(sessionId);
        return Map.of("success", deleted, "sessionId", sessionId);
    }
}