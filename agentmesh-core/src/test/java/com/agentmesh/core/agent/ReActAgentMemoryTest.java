package com.agentmesh.core.agent;

import com.agentmesh.core.llm.*;
import com.agentmesh.core.memory.MemoryManager;
import com.agentmesh.core.memory.MemoryProperties;
import com.agentmesh.core.memory.SlidingWindowMemoryManager;
import com.agentmesh.core.planning.*;
import com.agentmesh.core.session.ChatMessage;
import com.agentmesh.core.session.InMemoryConversationStore;
import com.agentmesh.core.tool.Tool;
import com.agentmesh.core.tool.ToolRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReActAgentMemoryTest {

    private LlmClient llmClient;
    private ToolRegistry toolRegistry;
    private MemoryManager memoryManager;
    private TaskPlanner taskPlanner;
    private PlanExecutor planExecutor;

    @BeforeEach
    void setUp() {
        llmClient = mock(LlmClient.class);
        when(llmClient.supportsFunctionCalling()).thenReturn(true);
        when(llmClient.getTokenEstimator()).thenReturn(text -> text.length());

        Tool<?, ?> testTool = new Tool<>() {
            @Override
            public String getId() { return "test_tool"; }
            @Override
            public String getDescription() { return "test tool"; }
            @Override
            public Map<String, Object> getInputSchema() { return Map.of(); }
            @Override
            public Object execute(Object input) { return Map.of("result", "ok"); }
        };
        toolRegistry = new ToolRegistry(List.of(testTool));

        memoryManager = mock(MemoryManager.class);
        taskPlanner = mock(TaskPlanner.class);
        planExecutor = mock(PlanExecutor.class);
    }

    @Test
    void shouldNotTriggerPlanningForSimpleTask() {
        when(llmClient.chatWithTools(any(), any(), any())).thenReturn(
                LlmChatResponse.builder().content("这是简单回复").finishReason("stop").build()
        );

        ReActAgent agent = new ReActAgent(llmClient, toolRegistry, 3, null,
                memoryManager, taskPlanner, planExecutor);

        var result = agent.run("提示词", "你好", List.of());

        assertNotNull(result.reply);
        assertEquals("这是简单回复", result.reply);
    }

    @Test
    void shouldTriggerPlanningForComplexTask() {
        TaskPlan mockPlan = TaskPlan.builder()
                .planId("p1")
                .goal("规划测试")
                .subTasks(List.of(
                        SubTask.builder().id("1").description("step1").dependsOn(List.of()).build()
                ))
                .build();
        when(taskPlanner.plan(any(), any(), any())).thenReturn(mockPlan);
        when(planExecutor.execute(any(), any())).thenReturn(
                PlanResult.builder()
                        .planId("p1")
                        .allSuccess(true)
                        .completedCount(1)
                        .summary("规划执行结果")
                        .subTaskResults(List.of())
                        .build()
        );

        ReActAgent agent = new ReActAgent(llmClient, toolRegistry, 3, null,
                memoryManager, taskPlanner, planExecutor);

        var result = agent.run("提示词", "帮我规划北京三日游", List.of());

        assertNotNull(result.reply);
        assertTrue(result.reply.contains("规划执行结果"));
    }

    @Test
    void shouldInjectMemory() {
        when(memoryManager.recall(any(), anyInt())).thenReturn(List.of(
                ChatMessage.builder().role("user").content("历史消息").build()
        ));
        when(memoryManager.getLongTermMemory(any(), any())).thenReturn("");
        when(llmClient.chatWithTools(any(), any(), any())).thenReturn(
                LlmChatResponse.builder().content("回复").finishReason("stop").build()
        );

        ReActAgent agent = new ReActAgent(llmClient, toolRegistry, 3, null,
                memoryManager, null, null);

        var result = agent.run("提示词", "你好", List.of());

        assertEquals("回复", result.reply);
    }

    @Test
    void shouldDegradeGracefullyWhenMemoryManagerIsNull() {
        when(llmClient.chatWithTools(any(), any(), any())).thenReturn(
                LlmChatResponse.builder().content("正常回复").finishReason("stop").build()
        );

        ReActAgent agent = new ReActAgent(llmClient, toolRegistry, 3, null,
                null, null, null);

        var result = agent.run("提示词", "你好", List.of());

        assertEquals("正常回复", result.reply);
    }

    @Test
    void shouldDegradeGracefullyWhenTaskPlannerIsNull() {
        when(memoryManager.recall(any(), anyInt())).thenReturn(List.of());
        when(memoryManager.getLongTermMemory(any(), any())).thenReturn("");
        when(llmClient.chatWithTools(any(), any(), any())).thenReturn(
                LlmChatResponse.builder().content("正常回复").finishReason("stop").build()
        );

        ReActAgent agent = new ReActAgent(llmClient, toolRegistry, 3, null,
                memoryManager, null, null);

        var result = agent.run("提示词", "帮我规划北京三日游", List.of());

        assertEquals("正常回复", result.reply);
    }
}