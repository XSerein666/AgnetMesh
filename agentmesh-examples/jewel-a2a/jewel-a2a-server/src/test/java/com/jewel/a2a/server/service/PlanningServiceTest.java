package com.jewel.a2a.server.service;

import com.agentmesh.core.planning.*;
import com.agentmesh.core.tool.ToolRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * PlanningService 单元测试：任务规划分解和执行。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PlanningService 单元测试")
class PlanningServiceTest {

    @Mock
    private TaskPlanner taskPlanner;

    @Mock
    private DagPlanExecutor dagPlanExecutor;

    @Mock
    private ToolRegistry toolRegistry;

    @Mock
    private com.agentmesh.core.task.TaskRepository taskRepository;

    @InjectMocks
    private PlanningService planningService;

    // ========== plan ==========

    @Nested
    @DisplayName("任务规划")
    class Plan {

        @Test
        @DisplayName("应返回非空的 TaskPlan")
        void shouldReturnNonNullPlan() {
            TaskPlan mockPlan = new TaskPlan();
            mockPlan.setPlanId("plan-1");
            mockPlan.setSubTasks(List.of());

            when(toolRegistry.toDefinitions()).thenReturn(List.of());
            when(taskPlanner.plan(anyString(), anyList(), anyString())).thenReturn(mockPlan);

            TaskPlan result = planningService.plan("设计一枚钻戒");

            assertNotNull(result);
            assertEquals("plan-1", result.getPlanId());
        }

        @Test
        @DisplayName("应将规划结果缓存")
        void shouldCachePlanResult() {
            TaskPlan mockPlan = new TaskPlan();
            mockPlan.setPlanId("plan-1");
            SubTask subTask = new SubTask();
            subTask.setId("sub-1");
            subTask.setDescription("分析需求");
            mockPlan.setSubTasks(List.of(subTask));

            when(toolRegistry.toDefinitions()).thenReturn(List.of());
            when(taskPlanner.plan(anyString(), anyList(), anyString())).thenReturn(mockPlan);

            TaskPlan plan = planningService.plan("设计一枚钻戒");

            // 应能通过 planId 执行（说明已缓存）
            PlanResult mockResult = PlanResult.builder()
                    .planId("plan-1")
                    .allSuccess(true)
                    .build();
            when(dagPlanExecutor.execute(any(TaskPlan.class), any())).thenReturn(mockResult);

            PlanResult execResult = planningService.execute(plan.getPlanId());
            assertNotNull(execResult);
            assertTrue(execResult.isAllSuccess());
        }

        @Test
        @DisplayName("规划包含子任务时应正确记录")
        void shouldRecordSubTaskCount() {
            TaskPlan mockPlan = new TaskPlan();
            mockPlan.setPlanId("plan-2");
            SubTask s1 = new SubTask();
            s1.setId("sub-1");
            s1.setDescription("解析图片");
            SubTask s2 = new SubTask();
            s2.setId("sub-2");
            s2.setDescription("生成设计");
            mockPlan.setSubTasks(List.of(s1, s2));

            when(toolRegistry.toDefinitions()).thenReturn(List.of());
            when(taskPlanner.plan(anyString(), anyList(), anyString())).thenReturn(mockPlan);

            TaskPlan result = planningService.plan("分析图片并生成设计");

            assertEquals(2, result.getSubTasks().size());
        }

        @Test
        @DisplayName("空目标应正常规划")
        void shouldHandleEmptyGoal() {
            TaskPlan mockPlan = new TaskPlan();
            mockPlan.setPlanId("plan-empty");
            mockPlan.setSubTasks(List.of());

            when(toolRegistry.toDefinitions()).thenReturn(List.of());
            when(taskPlanner.plan(eq(""), anyList(), anyString())).thenReturn(mockPlan);

            TaskPlan result = planningService.plan("");

            assertNotNull(result);
            assertTrue(result.getSubTasks().isEmpty());
        }
    }

    // ========== execute ==========

    @Nested
    @DisplayName("计划执行")
    class Execute {

        @Test
        @DisplayName("不存在的 planId 应抛出异常")
        void shouldThrowForUnknownPlanId() {
            assertThrows(IllegalArgumentException.class,
                    () -> planningService.execute("non-existent-plan"));
        }

        @Test
        @DisplayName("执行成功应返回 PlanResult")
        void shouldReturnPlanResultOnSuccess() {
            TaskPlan mockPlan = new TaskPlan();
            mockPlan.setPlanId("plan-1");
            mockPlan.setSubTasks(List.of());

            PlanResult mockResult = PlanResult.builder()
                    .planId("plan-1")
                    .allSuccess(true)
                    .build();

            when(toolRegistry.toDefinitions()).thenReturn(List.of());
            when(taskPlanner.plan(anyString(), anyList(), anyString())).thenReturn(mockPlan);
            when(dagPlanExecutor.execute(any(TaskPlan.class), any())).thenReturn(mockResult);

            planningService.plan("测试");
            PlanResult result = planningService.execute("plan-1");

            assertNotNull(result);
            assertEquals("plan-1", result.getPlanId());
        }

        @Test
        @DisplayName("执行失败应返回失败的 PlanResult")
        void shouldReturnFailedPlanResult() {
            TaskPlan mockPlan = new TaskPlan();
            mockPlan.setPlanId("plan-fail");
            SubTask subTask = new SubTask();
            subTask.setId("sub-1");
            subTask.setDescription("失败任务");
            mockPlan.setSubTasks(List.of(subTask));

            PlanResult mockResult = PlanResult.builder()
                    .planId("plan-fail")
                    .allSuccess(false)
                    .build();

            when(toolRegistry.toDefinitions()).thenReturn(List.of());
            when(taskPlanner.plan(anyString(), anyList(), anyString())).thenReturn(mockPlan);
            when(dagPlanExecutor.execute(any(TaskPlan.class), any())).thenReturn(mockResult);

            planningService.plan("测试");
            PlanResult result = planningService.execute("plan-fail");

            assertFalse(result.isAllSuccess());
        }
    }

    // ========== 子任务工具执行 ==========

    @Nested
    @DisplayName("子任务工具执行")
    class SubTaskToolExecution {

        @Test
        @DisplayName("有可用工具时应使用工具执行")
        void shouldExecuteWithTool() {
            TaskPlan mockPlan = new TaskPlan();
            mockPlan.setPlanId("plan-tool");
            SubTask subTask = new SubTask();
            subTask.setId("sub-1");
            subTask.setDescription("搜索知识库");
            subTask.setSuggestedTool("search_craft_knowledge");
            mockPlan.setSubTasks(List.of(subTask));

            when(toolRegistry.toDefinitions()).thenReturn(List.of());
            when(taskPlanner.plan(anyString(), anyList(), anyString())).thenReturn(mockPlan);
            when(dagPlanExecutor.execute(any(TaskPlan.class), any())).thenAnswer(invocation -> {
                return new PlanResult();
            });

            planningService.plan("搜索");
            PlanResult result = planningService.execute("plan-tool");

            assertNotNull(result);
        }
    }
}