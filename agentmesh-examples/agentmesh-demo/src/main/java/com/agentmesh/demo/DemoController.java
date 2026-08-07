package com.agentmesh.demo;

import com.agentmesh.core.agent.AgentConfig;
import com.agentmesh.core.agent.ConditionalOrchestrator;
import com.agentmesh.core.agent.ExecutionMode;
import com.agentmesh.core.agent.ParallelOrchestrator;
import com.agentmesh.core.agent.ReActAgent;
import com.agentmesh.core.agent.SequentialAgentOrchestrator;
import com.agentmesh.core.agent.SimpleOrchestrationPlan;
import com.agentmesh.core.llm.StreamEvent;
import com.agentmesh.core.protocol.AgentCard;
import com.agentmesh.core.protocol.ChatRequest;
import com.agentmesh.core.registry.AgentRegistry;
import com.agentmesh.core.session.ConversationStore;
import com.agentmesh.core.task.Task;
import com.agentmesh.core.task.TaskExecutor;
import com.agentmesh.core.task.TaskRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.Collections;
import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
public class DemoController {

    private final TaskExecutor taskExecutor;
    private final TaskRepository taskRepository;
    private final ConversationStore conversationStore;
    private final ReActAgent reActAgent;
    private final AgentConfig agentConfig;
    private final AgentCard agentCard;
    private final AgentRegistry agentRegistry;
    private final SequentialAgentOrchestrator sequentialAgentOrchestrator;
    private final ConditionalOrchestrator conditionalOrchestrator;
    private final ParallelOrchestrator parallelOrchestrator;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostMapping("/chat")
    public Map<String, String> chat(@RequestBody ChatRequest request) {
        String taskId = taskExecutor.submitChatTask(
                request.getSessionId(), request.getMessage());
        return Map.of("taskId", taskId);
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chatStream(@RequestBody ChatRequest request) {
        String sessionId = request.getSessionId();
        String userMessage = request.getMessage();

        return reActAgent.runStream(
                agentConfig.getSystemPrompt(),
                userMessage,
                conversationStore.getHistory(sessionId)
        ).map(event -> {
            String eventType = event.getType().name().toLowerCase();
            String data = toJson(event);
            return ServerSentEvent.<String>builder()
                    .event(eventType)
                    .data(data)
                    .build();
        }).doOnComplete(() -> {
            log.info("[SSE] 流式输出完成: sessionId={}", sessionId);
        }).doOnError(e -> log.error("[SSE] 流式输出异常: sessionId={}", sessionId, e));
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }

    @GetMapping("/task/{taskId}")
    public Object getTask(@PathVariable String taskId) {
        Task task = taskRepository.findById(taskId);
        if (task == null) {
            return Map.of("error", "任务不存在");
        }
        return Map.of(
                "taskId", task.getTaskId(),
                "status", task.getStatus().name(),
                "output", task.getOutput() != null ? task.getOutput() : Collections.emptyMap()
        );
    }

    @GetMapping("/history/{sessionId}")
    public Object getHistory(@PathVariable String sessionId) {
        return conversationStore.getHistory(sessionId);
    }

    @GetMapping("/.well-known/agent.json")
    public AgentCard getAgentCard() {
        return agentCard;
    }

    @GetMapping("/registry/agents")
    public Map<String, Object> getRegistryAgents() {
        return Map.of(
                "self", agentRegistry.self(),
                "peers", agentRegistry.discover()
        );
    }

    /**
     * 流式编排端点：根据 mode 分发到 Sequential 或 Conditional Orchestrator。
     *
     * 请求体示例：
     * - 顺序编排：{"message":"帮我分析图片并生成设计", "mode":"SEQUENTIAL"}
     * - 条件路由：{"message":"查询天气", "mode":"CONDITIONAL", "routingRule":"天气:0,珠宝:1"}
     */
    @PostMapping(value = "/orchestrate/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> orchestrateStream(@RequestBody Map<String, Object> request) {
        String input = (String) request.get("message");
        String mode = (String) request.getOrDefault("mode", "SEQUENTIAL");
        String routingRule = (String) request.get("routingRule");

        ExecutionMode executionMode;
        try {
            executionMode = ExecutionMode.valueOf(mode.toUpperCase());
        } catch (IllegalArgumentException e) {
            return Flux.just(ServerSentEvent.<String>builder()
                    .event("error")
                    .data("{\"type\":\"ERROR\",\"content\":\"不支持的编排模式: " + mode + "\"}")
                    .build());
        }

        SimpleOrchestrationPlan.Builder builder = SimpleOrchestrationPlan.builder()
                .mode(executionMode);

        if (routingRule != null && !routingRule.isEmpty()) {
            builder.routingRule(routingRule);
        }

        builder.addAgent(agentConfig);

        SimpleOrchestrationPlan plan = builder.build();

        Flux<StreamEvent> events = switch (executionMode) {
            case SEQUENTIAL  -> sequentialAgentOrchestrator.orchestrateStream(plan, input);
            case CONDITIONAL -> conditionalOrchestrator.orchestrateStream(plan, input);
            case PARALLEL    -> parallelOrchestrator.orchestrateStream(plan, input);
            case SUPERVISED, DEBATE, SWARM -> sequentialAgentOrchestrator.orchestrateStream(plan, input);
        };

        return events.map(event -> {
            String eventType = event.getType().name().toLowerCase();
            String data = toJson(event);
            return ServerSentEvent.<String>builder()
                    .event(eventType)
                    .data(data)
                    .build();
        });
    }
}