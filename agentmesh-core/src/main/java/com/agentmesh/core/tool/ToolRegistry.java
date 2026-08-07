package com.agentmesh.core.tool;

import com.agentmesh.core.infrastructure.AgentMeshMetrics;
import com.agentmesh.core.infrastructure.TraceIdContext;
import com.agentmesh.core.llm.ToolDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Tool 注册中心
 */
@Slf4j
public class ToolRegistry {

    private final Map<String, Tool<?, ?>> tools = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExecutorService toolExecutor;
    private final long executeTimeoutSeconds;
    private final AgentMeshMetrics metrics;

    private static final int DEFAULT_CORE_POOL_SIZE = 2;
    private static final int DEFAULT_MAX_POOL_SIZE = 10;
    private static final int DEFAULT_KEEP_ALIVE_SECONDS = 60;
    private static final long DEFAULT_EXECUTE_TIMEOUT_SECONDS = 30;

    public ToolRegistry(List<Tool<?, ?>> toolList) {
        this(toolList, null, DEFAULT_EXECUTE_TIMEOUT_SECONDS);
    }

    public ToolRegistry(List<Tool<?, ?>> toolList, long executeTimeoutSeconds) {
        this(toolList, null, executeTimeoutSeconds);
    }

    public ToolRegistry(List<Tool<?, ?>> toolList, AgentMeshMetrics metrics) {
        this(toolList, metrics, DEFAULT_EXECUTE_TIMEOUT_SECONDS);
    }

    public ToolRegistry(List<Tool<?, ?>> toolList, AgentMeshMetrics metrics,
                        long executeTimeoutSeconds) {
        for (Tool<?, ?> tool : toolList) {
            tools.put(tool.getId(), tool);
            log.info("[ToolRegistry] 注册 Tool: {}", tool.getId());
        }
        this.toolExecutor = new ThreadPoolExecutor(
                DEFAULT_CORE_POOL_SIZE,
                DEFAULT_MAX_POOL_SIZE,
                DEFAULT_KEEP_ALIVE_SECONDS, TimeUnit.SECONDS,
                new SynchronousQueue<>(),
                new ThreadPoolExecutor.AbortPolicy()
        );
        this.executeTimeoutSeconds = executeTimeoutSeconds;
        this.metrics = metrics;
    }

    /** 获取 Tool */
    public Tool<?, ?> getTool(String id) {
        return tools.get(id);
    }

    /**
     * 动态注册 Tool（供 RemoteToolRegistrar 使用）。
     * 覆盖语义：同名工具已存在时替换。
     */
    public void register(Tool<?, ?> tool) {
        tools.put(tool.getId(), tool);
        log.info("[ToolRegistry] 动态注册 Tool: {}", tool.getId());
    }

    /** 获取所有已注册的 Tool ID */
    public List<String> getAllToolIds() {
        return List.copyOf(tools.keySet());
    }

    /**
     * 将已注册工具转换为 ToolDefinition 列表（供 LLM function calling 使用）
     */
    public List<ToolDefinition> toDefinitions() {
        List<ToolDefinition> defs = new ArrayList<>();
        for (Map.Entry<String, Tool<?, ?>> entry : tools.entrySet()) {
            Tool<?, ?> tool = entry.getValue();
            defs.add(ToolDefinition.builder()
                    .name(tool.getId())
                    .description(tool.getDescription())
                    .parameters(tool.getInputSchema())
                    .build());
        }
        return defs;
    }

    /**
     * 将工具定义列表转为 JSON 字符串
     */
    public String toDefinitionsJson() {
        try {
            return objectMapper.writeValueAsString(toDefinitions());
        } catch (Exception e) {
            log.error("[ToolRegistry] 工具定义序列化失败", e);
            return "[]";
        }
    }

    /**
     * 校验工具输入参数是否符合 Schema 定义
     * @return 校验错误列表，空列表表示通过
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public List<String> validateInput(String toolId, Map<String, Object> input) {
        Tool tool = tools.get(toolId);
        if (tool == null) {
            return List.of("工具不存在: " + toolId);
        }
        Map<String, Object> schema = tool.getInputSchema();
        List<String> errors = new ArrayList<>();

        // 校验必填字段
        if (schema.containsKey("required")) {
            List<String> required = (List<String>) schema.get("required");
            Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
            if (properties != null) {
                for (String field : required) {
                    if (!input.containsKey(field)) {
                        errors.add("缺少必填参数: " + field);
                    }
                }
            }
        }

        // 校验类型
        if (schema.containsKey("properties")) {
            Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
            if (properties != null) {
                for (Map.Entry<String, Object> entry : input.entrySet()) {
                    Map<String, Object> propDef = (Map<String, Object>) properties.get(entry.getKey());
                    if (propDef != null) {
                        String expectedType = (String) propDef.get("type");
                        if (expectedType != null && !matchesType(entry.getValue(), expectedType)) {
                            errors.add("参数 " + entry.getKey() + " 类型不匹配，期望 " + expectedType);
                        }
                    }
                }
            }
        }

        return errors;
    }

    private boolean matchesType(Object value, String expectedType) {
        return switch (expectedType) {
            case "string" -> value instanceof String;
            case "number", "integer" -> value instanceof Number;
            case "boolean" -> value instanceof Boolean;
            case "array" -> value instanceof List;
            case "object" -> value instanceof Map;
            default -> true; // 未知类型宽松处理
        };
    }

    /**
     * 通过 Map 输入执行 Tool（供 ReActAgent 使用）
     * 将 Map 转换为 Tool 的泛型输入类型后执行
     * 包含超时控制和异常处理
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Object execute(String toolId, Map<String, Object> input) {
        Tool tool = tools.get(toolId);
        if (tool == null) {
            if (metrics != null) {
                metrics.recordToolExecution(toolId, "FAILED");
            }
            log.warn("[ToolRegistry] 工具不存在: toolId={}, traceId={}", toolId, TraceIdContext.get());
            return Map.of("error", "工具不存在: " + toolId);
        }

        Timer.Sample sample = metrics != null ? metrics.startToolTimer() : null;
        String traceId = TraceIdContext.get();
        log.info("[ToolRegistry] 执行工具: toolId={}, traceId={}", toolId, traceId);

        Future<Object> future = null;
        try {
            future = toolExecutor.submit(() -> tool.execute(input));
            Object result = future.get(executeTimeoutSeconds, TimeUnit.SECONDS);
            if (metrics != null) {
                metrics.recordToolExecution(toolId, "SUCCESS");
                metrics.stopToolTimer(sample, toolId);
            }
            log.info("[ToolRegistry] 工具执行完成: toolId={}, elapsedMs={}, traceId={}",
                    toolId, "N/A", traceId);
            return result;
        } catch (RejectedExecutionException e) {
            log.error("[ToolRegistry] 工具执行被拒绝（线程池已满）: toolId={}, traceId={}", toolId, traceId);
            if (metrics != null) {
                metrics.recordToolExecution(toolId, "FAILED");
                metrics.stopToolTimer(sample, toolId);
            }
            return Map.of("error", "系统繁忙，工具执行被拒绝: " + toolId);
        } catch (TimeoutException e) {
            log.error("[ToolRegistry] 工具执行超时: toolId={}, traceId={}", toolId, traceId);
            if (future != null) {
                future.cancel(true); // 中断正在执行的工具线程
            }
            if (metrics != null) {
                metrics.recordToolExecution(toolId, "TIMEOUT");
                metrics.stopToolTimer(sample, toolId);
            }
            return Map.of("error", "工具执行超时: " + toolId);
        } catch (Exception e) {
            log.error("[ToolRegistry] 工具执行异常: toolId={}, traceId={}", toolId, traceId, e);
            if (metrics != null) {
                metrics.recordToolExecution(toolId, "FAILED");
                metrics.stopToolTimer(sample, toolId);
            }
            return Map.of("error", "工具执行失败: " + e.getMessage());
        }
    }

    /**
     * 注销（移除）一个工具实例。
     * 如果 toolId 不存在，静默忽略（不抛异常）。
     * 新增于 v2.0，用于支持工具市场的卸载操作。
     */
    public void unregister(String toolId) {
        tools.remove(toolId);
        log.info("[ToolRegistry] 注销 Tool: {}", toolId);
    }

    public void shutdown() {
        toolExecutor.shutdown();
        try {
            if (!toolExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                toolExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            toolExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
