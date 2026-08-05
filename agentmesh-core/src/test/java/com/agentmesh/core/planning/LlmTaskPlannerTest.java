package com.agentmesh.core.planning;

import com.agentmesh.core.llm.LlmClient;
import com.agentmesh.core.llm.ToolDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LlmTaskPlannerTest {

    private LlmClient llmClient;
    private LlmTaskPlanner planner;

    @BeforeEach
    void setUp() {
        llmClient = mock(LlmClient.class);
        planner = new LlmTaskPlanner(llmClient, 10);
    }

    @Test
    void shouldPlanTasks() {
        when(llmClient.chat(any())).thenReturn(
                "{\"subTasks\": ["
                        + "{\"id\":\"1\",\"description\":\"搜索景点\",\"dependsOn\":[],\"suggestedTool\":\"search\"},"
                        + "{\"id\":\"2\",\"description\":\"选择酒店\",\"dependsOn\":[\"1\"],\"suggestedTool\":\"search_hotel\"}"
                        + "]}");

        List<ToolDefinition> tools = List.of(
                ToolDefinition.builder().name("search").description("搜索").build(),
                ToolDefinition.builder().name("search_hotel").description("搜索酒店").build()
        );

        TaskPlan plan = planner.plan("规划北京三日游", tools, "");

        assertNotNull(plan);
        assertEquals(2, plan.getSubTasks().size());
        assertEquals("1", plan.getSubTasks().get(0).getId());
        assertTrue(plan.getSubTasks().get(0).getDependsOn().isEmpty());
        assertEquals(List.of("1"), plan.getSubTasks().get(1).getDependsOn());
    }

    @Test
    void shouldReturnEmptyPlanOnInvalidJson() {
        when(llmClient.chat(any())).thenReturn("not a json");

        TaskPlan plan = planner.plan("规划", List.of(), "");

        assertTrue(plan.getSubTasks().isEmpty());
    }

    @Test
    void shouldReturnEmptyPlanForEmptyGoal() {
        TaskPlan plan = planner.plan("", List.of(), "");
        assertTrue(plan.getSubTasks().isEmpty());
    }

    @Test
    void shouldRespectMaxSubtasks() {
        StringBuilder json = new StringBuilder("{\"subTasks\": [");
        for (int i = 1; i <= 15; i++) {
            if (i > 1) json.append(",");
            json.append("{\"id\":\"").append(i).append("\",\"description\":\"task").append(i).append("\",\"dependsOn\":[]}");
        }
        json.append("]}");

        LlmTaskPlanner limitedPlanner = new LlmTaskPlanner(llmClient, 5);
        when(llmClient.chat(any())).thenReturn(json.toString());

        TaskPlan plan = limitedPlanner.plan("规划", List.of(), "");

        assertEquals(5, plan.getSubTasks().size());
    }

    @Test
    void shouldRemoveInvalidDependencies() {
        when(llmClient.chat(any())).thenReturn(
                "{\"subTasks\": ["
                        + "{\"id\":\"1\",\"description\":\"task1\",\"dependsOn\":[\"nonexistent\"],\"suggestedTool\":null}"
                        + "]}");

        TaskPlan plan = planner.plan("规划", List.of(), "");

        assertEquals(1, plan.getSubTasks().size());
        assertTrue(plan.getSubTasks().get(0).getDependsOn().isEmpty());
    }
}