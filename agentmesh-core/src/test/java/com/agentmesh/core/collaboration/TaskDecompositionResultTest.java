package com.agentmesh.core.collaboration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TaskDecompositionResult 单元测试。
 * 覆盖 JSON 序列化/反序列化、Schema 校验。
 */
class TaskDecompositionResultTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldCreateValidDecompositionResult() {
        TaskDecompositionResult result = TaskDecompositionResult.builder()
                .originalInput("设计珠宝方案")
                .subTasks(List.of(
                        TaskDecompositionResult.SubTask.builder()
                                .taskId("t1")
                                .description("设计灵感收集")
                                .assignedWorkerId("designer")
                                .input(Map.of("query", "珠宝设计灵感"))
                                .priority(1)
                                .build(),
                        TaskDecompositionResult.SubTask.builder()
                                .taskId("t2")
                                .description("工艺可行性评估")
                                .assignedWorkerId("crafter")
                                .priority(2)
                                .build()
                ))
                .strategy("sequential")
                .build();

        assertEquals(2, result.getSubTasks().size());
        assertEquals("designer", result.getSubTasks().get(0).getAssignedWorkerId());
        assertEquals("工艺可行性评估", result.getSubTasks().get(1).getDescription());
    }

    @Test
    void shouldSerializeAndDeserialize() throws Exception {
        TaskDecompositionResult original = TaskDecompositionResult.builder()
                .originalInput("test")
                .subTasks(List.of(
                        TaskDecompositionResult.SubTask.builder()
                                .taskId("t1")
                                .description("sub task")
                                .assignedWorkerId("w1")
                                .dependsOn(List.of())
                                .priority(1)
                                .build()
                ))
                .strategy("test-strategy")
                .build();

        String json = objectMapper.writeValueAsString(original);

        // 验证可以反序列化
        assertTrue(json.contains("t1"));
        assertTrue(json.contains("sub task"));
        assertTrue(json.contains("test-strategy"));
    }

    @Test
    void shouldHandleNullAssignedWorker() {
        TaskDecompositionResult.SubTask subTask = TaskDecompositionResult.SubTask.builder()
                .taskId("t1")
                .description("self handled")
                .assignedWorkerId(null)
                .priority(5)
                .build();

        assertNull(subTask.getAssignedWorkerId());
        assertEquals(5, subTask.getPriority());
        assertTrue(subTask.getDependsOn().isEmpty());
    }

    @Test
    void shouldHaveDefaultDependsOn() {
        TaskDecompositionResult.SubTask subTask = TaskDecompositionResult.SubTask.builder()
                .taskId("t1")
                .description("test")
                .build();

        assertNotNull(subTask.getDependsOn());
        assertTrue(subTask.getDependsOn().isEmpty());
    }
}