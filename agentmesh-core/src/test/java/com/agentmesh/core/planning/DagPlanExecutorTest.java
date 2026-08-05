package com.agentmesh.core.planning;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DagPlanExecutorTest {

    private DagPlanExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new DagPlanExecutor(2, Duration.ofSeconds(10), true);
    }

    @Test
    void shouldExecuteSequentialDag() {
        TaskPlan plan = TaskPlan.builder()
                .planId("p1")
                .goal("test")
                .subTasks(List.of(
                        SubTask.builder().id("1").description("step1").dependsOn(List.of()).build(),
                        SubTask.builder().id("2").description("step2").dependsOn(List.of("1")).build(),
                        SubTask.builder().id("3").description("step3").dependsOn(List.of("2")).build()
                ))
                .build();

        PlanResult result = executor.execute(plan, subTask -> "result: " + subTask.getDescription());

        assertTrue(result.isAllSuccess());
        assertEquals(3, result.getCompletedCount());
        assertEquals(0, result.getFailedCount());
    }

    @Test
    void shouldExecuteParallelDag() {
        TaskPlan plan = TaskPlan.builder()
                .planId("p1")
                .goal("test")
                .subTasks(List.of(
                        SubTask.builder().id("1").description("task1").dependsOn(List.of()).build(),
                        SubTask.builder().id("2").description("task2").dependsOn(List.of()).build(),
                        SubTask.builder().id("3").description("task3").dependsOn(List.of()).build()
                ))
                .build();

        PlanResult result = executor.execute(plan, subTask -> "result: " + subTask.getDescription());

        assertTrue(result.isAllSuccess());
        assertEquals(3, result.getCompletedCount());
    }

    @Test
    void shouldExecuteMixedDag() {
        TaskPlan plan = TaskPlan.builder()
                .planId("p1")
                .goal("test")
                .subTasks(List.of(
                        SubTask.builder().id("1").description("step1").dependsOn(List.of()).build(),
                        SubTask.builder().id("2").description("step2").dependsOn(List.of()).build(),
                        SubTask.builder().id("3").description("step3").dependsOn(List.of("1", "2")).build()
                ))
                .build();

        PlanResult result = executor.execute(plan, subTask -> "result: " + subTask.getDescription());

        assertTrue(result.isAllSuccess());
        assertEquals(3, result.getCompletedCount());
    }

    @Test
    void shouldRejectCyclicDependency() {
        TaskPlan plan = TaskPlan.builder()
                .planId("p1")
                .goal("test")
                .subTasks(List.of(
                        SubTask.builder().id("1").description("step1").dependsOn(List.of("2")).build(),
                        SubTask.builder().id("2").description("step2").dependsOn(List.of("1")).build()
                ))
                .build();

        PlanResult result = executor.execute(plan, subTask -> "result");

        assertFalse(result.isAllSuccess());
        assertTrue(result.getSummary().contains("循环依赖"));
    }

    @Test
    void shouldSkipDependentTasksOnFailure() {
        TaskPlan plan = TaskPlan.builder()
                .planId("p1")
                .goal("test")
                .subTasks(List.of(
                        SubTask.builder().id("1").description("step1").dependsOn(List.of()).build(),
                        SubTask.builder().id("2").description("step2").dependsOn(List.of("1")).build()
                ))
                .build();

        PlanResult result = executor.execute(plan, subTask -> {
            if ("1".equals(subTask.getId())) {
                throw new RuntimeException("step1 failed");
            }
            return "ok";
        });

        assertFalse(result.isAllSuccess());
        assertEquals(2, result.getFailedCount());
        assertEquals(0, result.getCompletedCount());
    }

    @Test
    void shouldHandleEmptyPlan() {
        TaskPlan plan = TaskPlan.builder()
                .planId("p1")
                .goal("test")
                .subTasks(List.of())
                .build();

        PlanResult result = executor.execute(plan, subTask -> "result");

        assertTrue(result.isAllSuccess());
        assertEquals(0, result.getCompletedCount());
    }

    @Test
    void shouldBuildSummary() {
        TaskPlan plan = TaskPlan.builder()
                .planId("p1")
                .goal("test goal")
                .subTasks(List.of(
                        SubTask.builder().id("1").description("step1").dependsOn(List.of()).build()
                ))
                .build();

        PlanResult result = executor.execute(plan, subTask -> "task result");

        assertNotNull(result.getSummary());
        assertTrue(result.getSummary().contains("test goal"));
        assertTrue(result.getSummary().contains("step1"));
    }
}