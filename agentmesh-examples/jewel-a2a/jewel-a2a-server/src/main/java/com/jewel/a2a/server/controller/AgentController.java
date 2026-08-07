package com.jewel.a2a.server.controller;

import com.jewel.a2a.common.dto.*;
import com.jewel.a2a.common.enums.TaskStatus;
import com.jewel.a2a.repository.entity.TaskEntity;
import com.jewel.a2a.server.service.AgentCardService;
import com.jewel.a2a.server.service.ChatService;
import com.jewel.a2a.server.service.SseEmitterService;
import com.jewel.a2a.server.service.TaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

/**
 * A2A 协议核心接口
 */
@RestController
@RequiredArgsConstructor
public class AgentController {

    private final AgentCardService agentCardService;
    private final TaskService taskService;
    private final SseEmitterService sseEmitterService;
    private final ChatService chatService;

    /**
     * 名片接口：GET /.well-known/agent.json
     */
    @GetMapping("/.well-known/agent.json")
    public AgentCard getAgentCard() {
        return agentCardService.buildAgentCard();
    }

    /**
     * 任务接口：POST /a2a/run（精确 skillId 路由）
     */
    @PostMapping("/a2a/run")
    public TaskResponse runTask(@RequestBody TaskRequest request) {
        return taskService.submitTask(request);
    }

    /**
     * 聊天接口：POST /a2a/chat（自然语言 + AgentScope 编排）
     */
    @PostMapping("/a2a/chat")
    public ChatResponse chat(@RequestBody ChatRequest request) {
        return chatService.submitChat(request);
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
}