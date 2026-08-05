package com.agentmesh.core.planning;

import com.agentmesh.core.llm.LlmClient;
import com.agentmesh.core.llm.ToolDefinition;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * LLM 驱动的任务规划实现。
 * 用 LLM 将用户目标分解为子任务 DAG。
 */
@Slf4j
public class LlmTaskPlanner implements TaskPlanner {

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;
    private final int maxSubtasks;

    public LlmTaskPlanner(LlmClient llmClient, int maxSubtasks) {
        this.llmClient = llmClient;
        this.objectMapper = new ObjectMapper();
        this.maxSubtasks = maxSubtasks;
    }

    @Override
    public TaskPlan plan(String goal, List<ToolDefinition> tools, String context) {
        if (goal == null || goal.isEmpty()) {
            return emptyPlan();
        }

        try {
            String toolsDesc = buildToolsDescription(tools);
            String prompt = buildPlanPrompt(goal, toolsDesc, context);
            String result = llmClient.chat(List.of(
                    Map.of("role", "user", "content", prompt)
            ));

            Map<String, Object> planMap = objectMapper.readValue(result,
                    new TypeReference<Map<String, Object>>() {});

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rawSubTasks = (List<Map<String, Object>>) planMap.get("subTasks");
            if (rawSubTasks == null || rawSubTasks.isEmpty()) {
                return emptyPlan();
            }

            // 限制子任务数量
            if (rawSubTasks.size() > maxSubtasks) {
                log.warn("[LlmTaskPlanner] 子任务数 {} 超过上限 {}，截断", rawSubTasks.size(), maxSubtasks);
            }

            List<SubTask> subTasks = new ArrayList<>();
            Set<String> subTaskIds = new HashSet<>();
            int count = 0;

            for (Map<String, Object> raw : rawSubTasks) {
                if (count >= maxSubtasks) break;
                String id = String.valueOf(raw.getOrDefault("id", String.valueOf(count + 1)));
                if (!subTaskIds.add(id)) continue; // 去重

                String description = String.valueOf(raw.getOrDefault("description", ""));
                @SuppressWarnings("unchecked")
                List<String> dependsOn = (List<String>) raw.getOrDefault("dependsOn", List.of());
                String suggestedTool = raw.get("suggestedTool") != null
                        ? String.valueOf(raw.get("suggestedTool")) : null;

                subTasks.add(SubTask.builder()
                        .id(id)
                        .description(description)
                        .dependsOn(dependsOn != null ? dependsOn : List.of())
                        .suggestedTool(suggestedTool)
                        .status(SubTaskStatus.PENDING)
                        .build());
                count++;
            }

            // 验证依赖关系：引用的前置任务必须存在
            for (SubTask subTask : subTasks) {
                if (subTask.getDependsOn() != null) {
                    for (String depId : subTask.getDependsOn()) {
                        if (!subTaskIds.contains(depId)) {
                            log.warn("[LlmTaskPlanner] 子任务 {} 依赖不存在的任务 {}，移除无效依赖",
                                    subTask.getId(), depId);
                            subTask.setDependsOn(subTask.getDependsOn().stream()
                                    .filter(subTaskIds::contains)
                                    .collect(Collectors.toList()));
                        }
                    }
                }
            }

            return TaskPlan.builder()
                    .planId(UUID.randomUUID().toString())
                    .goal(goal)
                    .subTasks(subTasks)
                    .createdAt(LocalDateTime.now())
                    .build();

        } catch (Exception e) {
            log.warn("[LlmTaskPlanner] 规划失败: {}", e.getMessage());
            return emptyPlan();
        }
    }

    private TaskPlan emptyPlan() {
        return TaskPlan.builder()
                .planId(UUID.randomUUID().toString())
                .subTasks(List.of())
                .createdAt(LocalDateTime.now())
                .build();
    }

    private String buildToolsDescription(List<ToolDefinition> tools) {
        if (tools == null || tools.isEmpty()) return "无可用工具";
        StringBuilder sb = new StringBuilder();
        for (ToolDefinition tool : tools) {
            sb.append("- ").append(tool.getName())
                    .append(": ").append(tool.getDescription()).append("\n");
        }
        return sb.toString();
    }

    private String buildPlanPrompt(String goal, String toolsDesc, String context) {
        return """
                你是一个任务规划专家。请将以下用户目标分解为子任务序列。
                
                用户目标：""" + goal + """
                
                可用工具：
                """ + toolsDesc + """
                
                上下文：
                """ + (context != null && !context.isEmpty() ? context : "无") + """
                
                请以 JSON 格式返回计划，每个子任务包含：
                - id: 子任务编号（字符串）
                - description: 子任务描述
                - dependsOn: 依赖的前置任务ID列表（无依赖时为空数组）
                - suggestedTool: 建议使用的工具名（从可用工具中选择，可选）
                
                返回格式：
                {"subTasks": [{"id": "1", "description": "搜索北京热门景点", "dependsOn": [], "suggestedTool": "search"}]}
                
                只返回 JSON，不要其他内容。""";
    }
}