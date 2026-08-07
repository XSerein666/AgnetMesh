package com.agentmesh.core.collaboration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FileWorkflowStateStore 测试。
 * 覆盖保存/加载/删除/清理过期、状态过期检查。
 */
class FileWorkflowStateStoreTest {

    @TempDir
    Path tempDir;

    private FileWorkflowStateStore stateStore;

    @BeforeEach
    void setUp() {
        stateStore = new FileWorkflowStateStore(tempDir.toString());
    }

    @Test
    void shouldSaveAndLoad() {
        WorkflowState state = WorkflowState.builder()
                .collaborationId("collab-1")
                .approvalId("approval-1")
                .status(WorkflowState.Status.AWAITING_APPROVAL)
                .approvalNode("工艺确认")
                .approvalContent("请确认工艺方案")
                .requestTime(Instant.now())
                .expireTime(Instant.now().plus(Duration.ofHours(24)))
                .callbackUrl("http://localhost/callback")
                .notifyChannel("console")
                .build();

        stateStore.save("collab-1", state);

        Optional<WorkflowState> loaded = stateStore.load("collab-1");
        assertTrue(loaded.isPresent());
        assertEquals("collab-1", loaded.get().getCollaborationId());
        assertEquals(WorkflowState.Status.AWAITING_APPROVAL, loaded.get().getStatus());
        assertEquals("工艺确认", loaded.get().getApprovalNode());
    }

    @Test
    void shouldSaveAndLoadWithWorkerResults() {
        Map<String, WorkerResult> completedResults = Map.of(
                "t1", WorkerResult.builder()
                        .taskId("t1")
                        .workerId("designer")
                        .status(WorkerResult.Status.SUCCESS)
                        .content("完成")
                        .durationMs(1000)
                        .build()
        );

        WorkflowState state = WorkflowState.builder()
                .collaborationId("collab-2")
                .approvalId("approval-2")
                .status(WorkflowState.Status.AWAITING_APPROVAL)
                .completedWorkerResults(completedResults)
                .contextSnapshot(Map.of("supervisor/plan", "plan-data"))
                .requestTime(Instant.now())
                .expireTime(Instant.now().plus(Duration.ofHours(24)))
                .build();

        stateStore.save("collab-2", state);

        Optional<WorkflowState> loaded = stateStore.load("collab-2");
        assertTrue(loaded.isPresent());
        assertNotNull(loaded.get().getCompletedWorkerResults());
        assertEquals(1, loaded.get().getCompletedWorkerResults().size());
        assertNotNull(loaded.get().getContextSnapshot());
    }

    @Test
    void shouldReturnEmptyForNonExistent() {
        Optional<WorkflowState> loaded = stateStore.load("nonexistent");
        assertTrue(loaded.isEmpty());
    }

    @Test
    void shouldDelete() {
        WorkflowState state = WorkflowState.builder()
                .collaborationId("collab-3")
                .status(WorkflowState.Status.AWAITING_APPROVAL)
                .requestTime(Instant.now())
                .expireTime(Instant.now().plus(Duration.ofHours(24)))
                .build();

        stateStore.save("collab-3", state);
        stateStore.delete("collab-3");

        assertTrue(stateStore.load("collab-3").isEmpty());
    }

    @Test
    void shouldCleanupExpired() {
        // 保存一个已过期的状态
        WorkflowState expired = WorkflowState.builder()
                .collaborationId("collab-expired")
                .status(WorkflowState.Status.AWAITING_APPROVAL)
                .requestTime(Instant.now().minus(Duration.ofHours(48)))
                .expireTime(Instant.now().minus(Duration.ofHours(24)))
                .build();

        // 保存一个未过期的状态
        WorkflowState valid = WorkflowState.builder()
                .collaborationId("collab-valid")
                .status(WorkflowState.Status.AWAITING_APPROVAL)
                .requestTime(Instant.now())
                .expireTime(Instant.now().plus(Duration.ofHours(24)))
                .build();

        stateStore.save("collab-expired", expired);
        stateStore.save("collab-valid", valid);

        int cleaned = stateStore.cleanupExpired(Duration.ofHours(1));
        assertTrue(cleaned >= 1, "应至少清理 1 个过期状态");

        // 过期状态应被清理
        assertTrue(stateStore.load("collab-expired").isEmpty());
        // 有效状态应保留
        assertTrue(stateStore.load("collab-valid").isPresent());
    }
}