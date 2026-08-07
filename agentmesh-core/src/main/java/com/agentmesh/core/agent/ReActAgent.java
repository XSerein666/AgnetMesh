package com.agentmesh.core.agent;

import com.agentmesh.core.infrastructure.AgentMeshMetrics;
import com.agentmesh.core.infrastructure.TraceIdContext;
import com.agentmesh.core.llm.LlmChatResponse;
import com.agentmesh.core.llm.LlmClient;
import com.agentmesh.core.llm.StreamEvent;
import com.agentmesh.core.llm.ToolCallRequest;
import com.agentmesh.core.llm.ToolChoice;
import com.agentmesh.core.llm.ToolDefinition;
import com.agentmesh.core.llm.TokenEstimator;
import com.agentmesh.core.memory.MemoryManager;
import com.agentmesh.core.planning.PlanExecutor;
import com.agentmesh.core.planning.TaskPlanner;
import com.agentmesh.core.session.ChatMessage;
import com.agentmesh.core.tool.ToolRegistry;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ReAct 引擎：推理 → 行动 → 观察 → 推理 ...
 * 支持双模式 function calling（原生 + 降级 JSON 解析）
 */
@Slf4j
public class ReActAgent {

    private static final int MAX_MESSAGE_TOKENS = 6000; // 约为 8K 上下文窗口的 75%

    private final LlmClient llmClient;
    private final ToolRegistry toolRegistry;
    private final ObjectMapper objectMapper;
    private final int maxLoops;
    private final AgentMeshMetrics metrics;

    // Phase 15：Memory + Planning
    private final MemoryManager memoryManager;
    private final TaskPlanner taskPlanner;
    private final PlanExecutor planExecutor;

    public ReActAgent(LlmClient llmClient, ToolRegistry toolRegistry) {
        this(llmClient, toolRegistry, 5, null, null, null, null);
    }

    public ReActAgent(LlmClient llmClient, ToolRegistry toolRegistry, int maxLoops) {
        this(llmClient, toolRegistry, maxLoops, null, null, null, null);
    }

    public ReActAgent(LlmClient llmClient, ToolRegistry toolRegistry, int maxLoops,
                       AgentMeshMetrics metrics) {
        this(llmClient, toolRegistry, maxLoops, metrics, null, null, null);
    }

    public ReActAgent(LlmClient llmClient, ToolRegistry toolRegistry, int maxLoops,
                       AgentMeshMetrics metrics,
                       MemoryManager memoryManager,
                       TaskPlanner taskPlanner,
                       PlanExecutor planExecutor) {
        this.llmClient = llmClient;
        this.toolRegistry = toolRegistry;
        this.objectMapper = new ObjectMapper();
        this.maxLoops = maxLoops;
        this.metrics = metrics;
        this.memoryManager = memoryManager;
        this.taskPlanner = taskPlanner;
        this.planExecutor = planExecutor;
    }

    /**
     * 执行 ReAct 循环（支持 Memory + Planning）
     */
    public AgentResult run(String systemPrompt, String userMessage, List<ChatMessage> history) {
        long startTime = System.currentTimeMillis();
        String traceId = TraceIdContext.get();
        log.info("[ReActAgent] 开始推理, traceId={}", traceId);

        // Phase 15：MemoryManager 召回压缩后的历史 + 长期记忆注入
        List<ChatMessage> effectiveHistory = history;
        if (memoryManager != null) {
            effectiveHistory = memoryManager.recall("default", MAX_MESSAGE_TOKENS);
            String longTermMemory = memoryManager.getLongTermMemory("default", userMessage);
            if (longTermMemory != null && !longTermMemory.isEmpty()) {
                systemPrompt = systemPrompt + longTermMemory;
            }
        }

        // Phase 15：复杂任务 → 规划执行
        if (taskPlanner != null && planExecutor != null && isComplexTask(userMessage)) {
            log.info("[ReActAgent] 检测到复杂任务，启用规划模式, traceId={}", traceId);
            return runWithPlanning(systemPrompt, userMessage, effectiveHistory, traceId, startTime);
        }

        List<Map<String, Object>> messages = buildMessages(systemPrompt, userMessage, effectiveHistory);
        List<Map<String, Object>> toolCalls = new ArrayList<>();
        boolean useNative = llmClient.supportsFunctionCalling();
        List<ToolDefinition> toolDefs = toolRegistry.toDefinitions();
        int consecutiveValidationFailures = 0;

        for (int loop = 0; loop < maxLoops; loop++) {
            log.info("[ReActAgent] 第 {} 轮推理, traceId={}", loop + 1, traceId);

            messages = truncateMessages(messages, llmClient.getTokenEstimator());

            if (useNative) {
                LlmChatResponse response = llmClient.chatWithTools(messages, toolDefs, ToolChoice.auto());
                if ("tool_calls".equals(response.getFinishReason()) && response.getToolCalls() != null) {
                    for (ToolCallRequest tc : response.getToolCalls()) {
                        Object result = toolRegistry.execute(tc.getName(), tc.getArguments());
                        recordToolCall(toolCalls, tc.getName(), tc.getArguments(), result);
                        if (result instanceof Map<?, ?> errorMap && errorMap.containsKey("error")) {
                            log.warn("[ReActAgent] 工具执行失败: tool={}, error={}",
                                    tc.getName(), errorMap.get("error"));
                        }
                        appendToolResult(messages, tc.getId(), tc.getName(), result);
                    }
                    continue;
                }
                long elapsed = System.currentTimeMillis() - startTime;
                log.info("[ReActAgent] 推理完成, totalLoops={}, totalElapsedMs={}, traceId={}",
                        loop + 1, elapsed, traceId);

                // Phase 15：后处理 — 追加记忆
                postProcess(userMessage, response.getContent());

                return buildResult(response.getContent(), toolCalls);
            } else {
                String response = llmClient.chat(messages);
                ParseResult parseResult = parseToolCall(response);
                switch (parseResult.status) {
                    case SUCCESS -> {
                        Object result = toolRegistry.execute(parseResult.toolCall.getTool(), parseResult.toolCall.getInput());
                        recordToolCall(toolCalls, parseResult.toolCall.getTool(), parseResult.toolCall.getInput(), result);
                        messages.add(message("assistant", response));
                        messages.add(buildToolResultMessage(parseResult.toolCall.getTool(), result));
                        consecutiveValidationFailures = 0;
                    }
                    case VALIDATION_FAILED -> {
                        consecutiveValidationFailures++;
                        if (consecutiveValidationFailures > 2) {
                            log.warn("[ReActAgent] 连续校验失败 {} 次，终止循环", consecutiveValidationFailures);
                            long elapsed = System.currentTimeMillis() - startTime;
                            log.info("[ReActAgent] 推理异常终止, totalLoops={}, totalElapsedMs={}, traceId={}",
                                    loop + 1, elapsed, traceId);

                            postProcess(userMessage, "抱歉，我暂时无法正确处理您的请求，请稍后再试。");

                            return buildResult("抱歉，我暂时无法正确处理您的请求，请稍后再试。", toolCalls);
                        }
                        log.warn("[ReActAgent] 降级模式校验失败 (第{}次): {}", consecutiveValidationFailures, parseResult.errorDetail);
                        messages.add(message("user", "[校验错误] " + parseResult.errorDetail + "。请修正后重试。"));
                    }
                    case NOT_JSON -> {
                        long elapsed = System.currentTimeMillis() - startTime;
                        log.info("[ReActAgent] 推理完成(非JSON), totalLoops={}, totalElapsedMs={}, traceId={}",
                                loop + 1, elapsed, traceId);

                        postProcess(userMessage, response);

                        return buildResult(response, toolCalls);
                    }
                    default -> {
                        // 无效状态，忽略
                    }
                }
            }
        }

        long elapsed = System.currentTimeMillis() - startTime;
        log.info("[ReActAgent] 推理超限终止, totalLoops={}, totalElapsedMs={}, traceId={}",
                maxLoops, elapsed, traceId);

        postProcess(userMessage, "抱歉，处理过程比较复杂，请稍后再试。");

        return buildResult("抱歉，处理过程比较复杂，请稍后再试。", toolCalls);
    }

    /**
     * Phase 15：带规划的推理
     */
    private AgentResult runWithPlanning(String systemPrompt, String userMessage,
                                         List<ChatMessage> history, String traceId, long startTime) {
        try {
            List<ToolDefinition> toolDefs = toolRegistry.toDefinitions();
            String context = buildContextFromHistory(history);
            var plan = taskPlanner.plan(userMessage, toolDefs, context);

            if (plan.getSubTasks() == null || plan.getSubTasks().isEmpty()) {
                log.info("[ReActAgent] 规划结果为空，回退到 ReAct 循环");
                // 回退逻辑已在 run() 中处理，这里直接重新调用
                return buildResult("无法为该任务生成执行计划，请尝试更具体地描述需求。", List.of());
            }

            log.info("[ReActAgent] 规划完成: planId={}, subTasks={}",
                    plan.getPlanId(), plan.getSubTasks().size());

            var result = planExecutor.execute(plan, subTask -> {
                try {
                    // 如果有建议工具，优先使用
                    if (subTask.getSuggestedTool() != null && !subTask.getSuggestedTool().isEmpty()) {
                        Object toolResult = toolRegistry.execute(subTask.getSuggestedTool(),
                                Map.of("query", subTask.getDescription()));
                        return toJson(toolResult);
                    }
                    // 否则用 LLM 执行子任务
                    return llmClient.chat(List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", subTask.getDescription())
                    ));
                } catch (Exception e) {
                    throw new RuntimeException("子任务执行失败: " + e.getMessage(), e);
                }
            });

            long elapsed = System.currentTimeMillis() - startTime;
            log.info("[ReActAgent] 规划执行完成: planId={}, allSuccess={}, elapsedMs={}",
                    plan.getPlanId(), result.isAllSuccess(), elapsed);

            postProcess(userMessage, result.getSummary());

            AgentResult agentResult = new AgentResult();
            agentResult.reply = result.getSummary();
            agentResult.toolCalls = List.of();
            return agentResult;

        } catch (Exception e) {
            log.warn("[ReActAgent] 规划执行失败，回退到 ReAct 循环: {}", e.getMessage());
            return buildResult("任务规划执行失败: " + e.getMessage(), List.of());
        }
    }

    /**
     * Phase 15：对话后处理 — 追加到 MemoryManager + 提取长期记忆
     */
    private void postProcess(String userMessage, String reply) {
        if (memoryManager == null) {
            return;
        }

        try {
            memoryManager.append("default", ChatMessage.builder()
                    .role("user").content(userMessage).build());
            memoryManager.append("default", ChatMessage.builder()
                    .role("assistant").content(reply).build());
        } catch (Exception e) {
            log.warn("[ReActAgent] 记忆追加失败: {}", e.getMessage());
        }
    }

    /**
     * Phase 15：判断是否为复杂任务
     */
    private boolean isComplexTask(String userMessage) {
        if (userMessage == null) {
            return false;
        }
        String msg = userMessage.toLowerCase();
        String[] complexKeywords = {"规划", "安排", "流程", "步骤", "计划", "方案", "攻略",
                "plan", "schedule", "step", "workflow"};
        for (String keyword : complexKeywords) {
            if (msg.contains(keyword)) {
                return true;
            }
        }
        return userMessage.length() > 200;
    }

    private String buildContextFromHistory(List<ChatMessage> history) {
        if (history == null || history.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (ChatMessage msg : history) {
            if (!"system".equals(msg.getRole())) {
                sb.append(msg.getRole()).append(": ").append(msg.getContent()).append("\n");
            }
        }
        return sb.toString();
    }

    /**
     * 流式执行 ReAct 循环
     * @return SSE 事件流
     */
    public Flux<StreamEvent> runStream(String systemPrompt, String userMessage,
                                        List<ChatMessage> history) {
        return Flux.defer(() -> {
            long startTime = System.currentTimeMillis();
            String traceId = TraceIdContext.get();
            log.info("[ReActAgent] 开始流式推理, traceId={}", traceId);

            List<Map<String, Object>> messages = buildMessages(systemPrompt, userMessage, history);
            boolean useNative = llmClient.supportsFunctionCalling();
            List<ToolDefinition> toolDefs = toolRegistry.toDefinitions();

            return recursiveStreamStep(messages, toolDefs, useNative, 0, traceId, startTime)
                    .doOnComplete(() -> {
                        long elapsed = System.currentTimeMillis() - startTime;
                        log.info("[ReActAgent] 流式推理完成, totalElapsedMs={}, traceId={}", elapsed, traceId);
                    })
                    .doOnError(e -> {
                        long elapsed = System.currentTimeMillis() - startTime;
                        log.error("[ReActAgent] 流式推理异常, totalElapsedMs={}, traceId={}", elapsed, traceId, e);
                    });
        });
    }

    /**
     * 递归流式步骤：每一轮推理通过 Flux 发射增量事件
     */
    private Flux<StreamEvent> recursiveStreamStep(List<Map<String, Object>> messages,
                                                   List<ToolDefinition> toolDefs,
                                                   boolean useNative, int loop,
                                                   String traceId, long startTime) {
        if (loop >= maxLoops) {
            long elapsed = System.currentTimeMillis() - startTime;
            log.info("[ReActAgent] 流式推理超限终止, totalLoops={}, totalElapsedMs={}, traceId={}",
                    maxLoops, elapsed, traceId);
            return Flux.just(StreamEvent.builder().type(StreamEvent.Type.DONE).build());
        }

        log.info("[ReActAgent] 第 {} 轮流式推理, traceId={}", loop + 1, traceId);

        // 每轮推理开始发送 thinking 事件
        Flux<StreamEvent> thinking = Flux.just(
                StreamEvent.builder()
                        .type(StreamEvent.Type.THINKING)
                        .content("第 " + (loop + 1) + " 轮推理...")
                        .build()
        );

        if (useNative) {
            // 累积工具调用信息（数组用于在 lambda 中可变）
            final String[] pendingToolName = {null};
            final String[] pendingToolCallId = {null};
            final StringBuilder[] pendingArgsBuilder = {new StringBuilder()};

            Flux<StreamEvent> llmStream = llmClient.chatWithToolsStream(messages, toolDefs, ToolChoice.auto())
                    .concatMap(event -> {
                        switch (event.getType()) {
                            case TOOL_CALL_START -> {
                                pendingToolName[0] = event.getToolName();
                                pendingToolCallId[0] = event.getToolCallId();
                                pendingArgsBuilder[0] = new StringBuilder();
                                // 首段 arguments 也可能在 TOOL_CALL_START 中携带
                                if (event.getContent() != null) {
                                    pendingArgsBuilder[0].append(event.getContent());
                                }
                                return Flux.just(event);
                            }
                            case TOOL_CALL_ARGS -> {
                                if (event.getContent() != null) {
                                    pendingArgsBuilder[0].append(event.getContent());
                                }
                                return Flux.just(event);
                            }
                            case TOOL_CALL_END -> {
                                String toolName = pendingToolName[0] != null
                                        ? pendingToolName[0] : event.getToolName();
                                // 使用后重置 pending 状态，避免 DONE 事件误触发工具执行
                                pendingToolName[0] = null;
                                pendingToolCallId[0] = null;
                                // 优先使用累积的参数，其次使用 event 中的参数
                                Map<String, Object> args = event.getArguments();
                                if (args == null || args.isEmpty()) {
                                    String accumulatedJson = pendingArgsBuilder[0].toString();
                                    if (!accumulatedJson.isEmpty()) {
                                        try {
                                            args = objectMapper.readValue(accumulatedJson, Map.class);
                                        } catch (Exception e) {
                                            log.warn("[ReActAgent] 累积参数 JSON 解析失败: {}", accumulatedJson);
                                            args = Collections.emptyMap();
                                        }
                                    } else {
                                        args = Collections.emptyMap();
                                    }
                                }

                                Object result = toolRegistry.execute(toolName, args);
                                log.info("[ReActAgent] 工具执行完成: tool={}, args={}, result={}", toolName, args, result);

                                List<Map<String, Object>> messagesCopy = new ArrayList<>(messages);
                                appendToolResult(messagesCopy, pendingToolCallId[0], toolName, result);

                                StreamEvent toolResult = StreamEvent.builder()
                                        .type(StreamEvent.Type.TOOL_RESULT)
                                        .toolName(toolName)
                                        .result(result)
                                        .build();

                                return Flux.concat(
                                        Flux.just(event),        // TOOL_CALL_END
                                        Flux.just(toolResult),    // TOOL_RESULT
                                        recursiveStreamStep(messagesCopy, toolDefs, true, loop + 1, traceId, startTime)
                                );
                            }
                            case TEXT, THINKING, TOOL_RESULT -> {
                                return Flux.just(event);
                            }
                            case DONE, ERROR -> {
                                // 如果 DONE 前有未完成的工具调用，自动执行
                                if (pendingToolName[0] != null) {
                                    String toolName = pendingToolName[0];
                                    Map<String, Object> args = Collections.emptyMap();
                                    String accumulatedJson = pendingArgsBuilder[0].toString();
                                    if (!accumulatedJson.isEmpty()) {
                                        try {
                                            args = objectMapper.readValue(accumulatedJson, Map.class);
                                        } catch (Exception e) {
                                            log.warn("[ReActAgent] 累积参数 JSON 解析失败: {}", accumulatedJson);
                                        }
                                    }

                                    Object result = toolRegistry.execute(toolName, args);
                                    log.info("[ReActAgent] 工具执行完成(DONE触发): tool={}, args={}, result={}", toolName, args, result);

                                    List<Map<String, Object>> messagesCopy = new ArrayList<>(messages);
                                    appendToolResult(messagesCopy, pendingToolCallId[0], toolName, result);

                                    StreamEvent toolResult = StreamEvent.builder()
                                            .type(StreamEvent.Type.TOOL_RESULT)
                                            .toolName(toolName)
                                            .result(result)
                                            .build();

                                    // 重置 pending 状态
                                    pendingToolName[0] = null;
                                    pendingToolCallId[0] = null;

                                    return Flux.concat(
                                            Flux.just(toolResult),
                                            recursiveStreamStep(messagesCopy, toolDefs, true, loop + 1, traceId, startTime)
                                    );
                                }
                                return Flux.just(event);
                            }
                            default -> {
                                return Flux.just(event);
                            }
                        }
                    });
            return Flux.concat(thinking, llmStream);
        } else {
            return thinking.concatWith(Flux.defer(() -> {
                String response = llmClient.chat(messages);
                ParseResult parseResult = parseToolCall(response);
                if (parseResult.status == ParseResult.Status.SUCCESS) {
                    Object result = toolRegistry.execute(parseResult.toolCall.getTool(),
                            parseResult.toolCall.getInput());
                    appendToolResult(messages, "fallback_" + parseResult.toolCall.getTool(),
                            parseResult.toolCall.getTool(), result);
                    List<Map<String, Object>> messagesCopy = new ArrayList<>(messages);
                    return Flux.concat(
                            Flux.just(StreamEvent.builder()
                                    .type(StreamEvent.Type.TOOL_CALL_START)
                                    .toolName(parseResult.toolCall.getTool())
                                    .build()),
                            Flux.just(StreamEvent.builder()
                                    .type(StreamEvent.Type.TOOL_RESULT)
                                    .toolName(parseResult.toolCall.getTool())
                                    .result(result)
                                    .build()),
                            recursiveStreamStep(messagesCopy, toolDefs, false, loop + 1, traceId, startTime)
                    );
                }
                return Flux.just(
                        StreamEvent.builder().type(StreamEvent.Type.TEXT).content(response).build(),
                        StreamEvent.builder().type(StreamEvent.Type.DONE).build()
                );
            }));
        }
    }

    // ========== 消息构建 ==========

    private List<Map<String, Object>> buildMessages(String systemPrompt, String userMessage,
                                                     List<ChatMessage> history) {
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(message("system", systemPrompt));

        for (ChatMessage msg : history) {
            if ("tool".equals(msg.getRole())) {
                if (llmClient.supportsToolRole()) {
                    Map<String, Object> toolMsg = new HashMap<>();
                    toolMsg.put("role", "tool");
                    toolMsg.put("tool_call_id", "fallback_" + msg.getToolName());
                    toolMsg.put("name", msg.getToolName());
                    toolMsg.put("content", msg.getContent());
                    messages.add(toolMsg);
                } else {
                    messages.add(message("user",
                            "[工具结果] " + msg.getToolName() + ": " + msg.getContent()));
                }
            } else {
                messages.add(message(msg.getRole(), msg.getContent()));
            }
        }

        messages.add(message("user", userMessage));
        return messages;
    }

    private Map<String, Object> message(String role, String content) {
        Map<String, Object> msg = new HashMap<>();
        msg.put("role", role);
        msg.put("content", content);
        return msg;
    }

    private Map<String, Object> buildToolResultMessage(String toolName, Object result) {
        if (llmClient.supportsToolRole()) {
            Map<String, Object> msg = new HashMap<>();
            msg.put("role", "tool");
            msg.put("tool_call_id", "fallback_" + toolName);
            msg.put("name", toolName);
            msg.put("content", toJson(result));
            return msg;
        } else {
            return message("user", "[工具结果] " + toolName + ": " + toJson(result));
        }
    }

    private void appendToolResult(List<Map<String, Object>> messages, String toolCallId,
                                   String toolName, Object result) {
        if (llmClient.supportsToolRole() && toolCallId != null) {
            Map<String, Object> msg = new HashMap<>();
            msg.put("role", "tool");
            msg.put("tool_call_id", toolCallId);
            msg.put("name", toolName);
            msg.put("content", toJson(result));
            messages.add(msg);
        } else {
            messages.add(message("user", "[工具结果] " + toolName + ": " + toJson(result)));
        }
    }

    // ========== 降级模式解析 ==========

    private ParseResult parseToolCall(String response) {
        String trimmed = response.trim();
        if (!trimmed.startsWith("{")) {
            return ParseResult.notJson();
        }
        try {
            Map<String, Object> map = objectMapper.readValue(trimmed,
                    new TypeReference<Map<String, Object>>() {});
            if (!map.containsKey("tool") || !map.containsKey("input")) {
                return ParseResult.notJson();
            }
            String toolName = (String) map.get("tool");
            @SuppressWarnings("unchecked")
            Map<String, Object> input = (Map<String, Object>) map.get("input");

            // 1. 工具名白名单校验
            if (!toolRegistry.getAllToolIds().contains(toolName)) {
                return ParseResult.validationFailed("工具不存在: " + toolName);
            }

            // 2. 参数 Schema 基础校验
            List<String> errors = toolRegistry.validateInput(toolName, input);
            if (!errors.isEmpty()) {
                return ParseResult.validationFailed("参数校验失败: " + String.join(", ", errors));
            }

            com.agentmesh.core.llm.ToolCall tc = new com.agentmesh.core.llm.ToolCall();
            tc.setTool(toolName);
            tc.setInput(input);
            return ParseResult.success(tc);

        } catch (Exception e) {
            return ParseResult.notJson();
        }
    }

    // ========== 消息截断 ==========

    private List<Map<String, Object>> truncateMessages(List<Map<String, Object>> messages,
                                                        TokenEstimator estimator) {
        int totalTokens = messages.stream()
                .mapToInt(m -> estimateMessageTokens(m, estimator))
                .sum();
        if (totalTokens <= MAX_MESSAGE_TOKENS) {
            return messages;
        }

        List<Map<String, Object>> truncated = new ArrayList<>();
        truncated.add(messages.get(0)); // 始终保留 system prompt

        List<Map<String, Object>> recentMessages = new ArrayList<>();
        int currentTokens = estimateMessageTokens(messages.get(0), estimator);
        for (int i = messages.size() - 1; i >= 1; i--) {
            int msgTokens = estimateMessageTokens(messages.get(i), estimator);
            if (currentTokens + msgTokens > MAX_MESSAGE_TOKENS) {
                log.warn("[ReActAgent] 消息列表超出 Token 上限，截断早期消息，保留最近 {} 轮",
                        messages.size() - i - 1);
                break;
            }
            recentMessages.add(0, messages.get(i)); // 保持顺序
            currentTokens += msgTokens;
        }
        truncated.addAll(recentMessages);
        return truncated;
    }

    private int estimateMessageTokens(Map<String, Object> msg, TokenEstimator estimator) {
        Object content = msg.get("content");
        if (content instanceof String s) {
            return estimator.estimateTokens(s);
        } else if (content instanceof List) {
            return estimator.estimateTokens(content.toString());
        } else if (content instanceof Map) {
            return estimator.estimateTokens(content.toString());
        }
        return 0;
    }

    // ========== 降级模式 ToolChoice 近似 ==========

    /**
     * 降级模式下通过 Prompt 注入实现近似 ToolChoice 效果
     */
    public String buildFallbackPrompt(String systemPrompt, ToolChoice toolChoice) {
        if (toolChoice == null || toolChoice.isAuto()) {
            return systemPrompt;
        }
        if (toolChoice.isRequired()) {
            return systemPrompt + "\n\n【重要】你必须调用工具来回答用户问题，不要直接回复。"
                    + "请严格返回JSON格式：{\"tool\":\"工具名\",\"input\":{...}}";
        }
        if (toolChoice.isSpecific()) {
            return systemPrompt + "\n\n【重要】你必须使用 " + toolChoice.getSpecificToolName()
                    + " 工具来回答。请严格返回JSON格式：{\"tool\":\""
                    + toolChoice.getSpecificToolName() + "\",\"input\":{...}}";
        }
        return systemPrompt;
    }

    // ========== 辅助方法 ==========

    private void recordToolCall(List<Map<String, Object>> toolCalls, String toolName,
                                 Map<String, Object> input, Object result) {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("tool", toolName);
        record.put("input", input);
        record.put("result", result);
        toolCalls.add(record);
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return obj.toString();
        }
    }

    private AgentResult buildResult(String reply, List<Map<String, Object>> toolCalls) {
        AgentResult result = new AgentResult();
        result.reply = reply;
        result.toolCalls = toolCalls;
        return result;
    }

    // ========== 内部类型 ==========

    private static class ParseResult {
        enum Status { SUCCESS, VALIDATION_FAILED, NOT_JSON }

        final Status status;
        final com.agentmesh.core.llm.ToolCall toolCall;
        final String errorDetail;

        private ParseResult(Status status, com.agentmesh.core.llm.ToolCall toolCall, String errorDetail) {
            this.status = status;
            this.toolCall = toolCall;
            this.errorDetail = errorDetail;
        }

        static ParseResult success(com.agentmesh.core.llm.ToolCall tc) {
            return new ParseResult(Status.SUCCESS, tc, null);
        }
        static ParseResult validationFailed(String detail) {
            return new ParseResult(Status.VALIDATION_FAILED, null, detail);
        }
        static ParseResult notJson() {
            return new ParseResult(Status.NOT_JSON, null, null);
        }
    }

    public static class AgentResult {
        public String reply;
        public List<Map<String, Object>> toolCalls;
    }
}
