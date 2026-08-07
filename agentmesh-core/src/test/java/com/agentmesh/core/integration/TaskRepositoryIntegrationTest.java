package com.agentmesh.core.integration;

import com.agentmesh.core.task.JdbcTaskRepository;
import com.agentmesh.core.task.Task;
import com.agentmesh.core.task.TaskRepository;
import com.agentmesh.core.task.TaskStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 任务仓库集成测试：验证 JdbcTaskRepository 的 CRUD 和乐观锁机制。
 */
@DisplayName("任务仓库集成测试")
class TaskRepositoryIntegrationTest {

    private TaskRepository repository;

    @BeforeEach
    void setUp() {
        repository = new JdbcTaskRepository();
    }

    @Test
    @DisplayName("保存任务并查询")
    void shouldSaveAndFindById() {
        Task task = new Task("task-001", "get_weather",
                Map.of("city", "北京"));

        repository.save(task);

        Task found = repository.findById("task-001");
        assertThat(found).isNotNull();
        assertThat(found.getTaskId()).isEqualTo("task-001");
        assertThat(found.getSkillId()).isEqualTo("get_weather");
        assertThat(found.getStatus()).isEqualTo(TaskStatus.PENDING);
        assertThat(found.getInput()).containsEntry("city", "北京");
    }

    @Test
    @DisplayName("查询不存在的任务返回 null")
    void shouldReturnNullForNonExistentTask() {
        assertThat(repository.findById("no-such-task")).isNull();
    }

    @Test
    @DisplayName("更新任务状态和输出")
    void shouldUpdateTaskStatus() {
        Task task = new Task("task-001", "get_weather",
                Map.of("city", "上海"));
        repository.save(task);

        repository.updateStatus("task-001", TaskStatus.SUCCESS,
                Map.of("temperature", "25°C"));

        Task updated = repository.findById("task-001");
        assertThat(updated.getStatus()).isEqualTo(TaskStatus.SUCCESS);
        assertThat(updated.getOutput()).isNotNull();
        assertThat(updated.getVersion()).isEqualTo(1);
    }

    @Test
    @DisplayName("乐观锁版本号递增")
    void shouldIncrementVersionOnUpdate() {
        Task task = new Task("task-001", "get_weather", Map.of());
        repository.save(task);
        assertThat(task.getVersion()).isEqualTo(0);

        repository.updateStatus("task-001", TaskStatus.RUNNING, null);
        assertThat(repository.findById("task-001").getVersion()).isEqualTo(1);

        repository.updateStatus("task-001", TaskStatus.SUCCESS,
                Map.of("result", "ok"));
        assertThat(repository.findById("task-001").getVersion()).isEqualTo(2);
    }

    @Test
    @DisplayName("更新不存在的任务不抛异常")
    void shouldNotThrowOnUpdateNonExistent() {
        // 不应抛出异常
        repository.updateStatus("no-such-task", TaskStatus.SUCCESS, "result");
    }

    @Test
    @DisplayName("任务状态流转：PENDING → RUNNING → SUCCESS")
    void shouldTransitionThroughStatuses() {
        Task task = new Task("task-001", "search", Map.of("q", "test"));
        repository.save(task);

        assertThat(repository.findById("task-001").getStatus())
                .isEqualTo(TaskStatus.PENDING);

        repository.updateStatus("task-001", TaskStatus.RUNNING, null);
        assertThat(repository.findById("task-001").getStatus())
                .isEqualTo(TaskStatus.RUNNING);

        repository.updateStatus("task-001", TaskStatus.SUCCESS,
                Map.of("results", "found"));
        assertThat(repository.findById("task-001").getStatus())
                .isEqualTo(TaskStatus.SUCCESS);
    }

    @Test
    @DisplayName("任务状态流转：PENDING → RUNNING → FAILED")
    void shouldTransitionToFailed() {
        Task task = new Task("task-001", "weather", Map.of());
        repository.save(task);

        repository.updateStatus("task-001", TaskStatus.RUNNING, null);
        repository.updateStatus("task-001", TaskStatus.FAILED, "服务不可用");

        Task failed = repository.findById("task-001");
        assertThat(failed.getStatus()).isEqualTo(TaskStatus.FAILED);
        assertThat(failed.getOutput()).isEqualTo("服务不可用");
    }
}