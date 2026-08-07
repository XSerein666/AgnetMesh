package com.agentmesh.core.collaboration;

import com.agentmesh.core.agent.AgentConfig;
import com.agentmesh.core.llm.StreamEvent;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ApprovalGate 测试。
 * 覆盖正常审批通过、审批驳回、审批超时、回调幂等。
 */
class ApprovalGateTest {

    @TempDir
    Path tempDir;

    private ApprovalGate approvalGate;
    private FileWorkflowStateStore stateStore;
    private CollaborationMetrics collabMetrics;
    private InMemoryMessageBus messageBus;

    @BeforeEach
    void setUp() {
        collabMetrics = new CollaborationMetrics(new SimpleMeterRegistry());
        stateStore = new FileWorkflowStateStore(tempDir.toString());
        messageBus = new InMemoryMessageBus(256, collabMetrics);
        approvalGate = new ApprovalGate(stateStore, Duration.ofHours(24), collabMetrics);
    }

    @AfterEach
    void tearDown() {
        messageBus.close();
    }

    @Test
    void shouldRequestApprovalAndSaveState() {
        ApprovalRequest request = ApprovalRequest.builder()
                .approvalId("approval-1")
                .collaborationId("collab-1")
                .approvalNode("工艺确认")
                .content("请确认工艺方案")
                .callbackUrl("http://localhost/callback")
                .notifyChannel("console")
                .build();

        SharedContext context = new SharedContext("collab-1");
        context.put("supervisor/plan", "plan-data", "supervisor-1", "supervisor");

        Flux<StreamEvent> events = approvalGate.requestApproval(request, context, Map.of());

        StepVerifier.create(events)
                .expectNextMatches(e -> e.getType() == StreamEvent.Type.THINKING)
                .expectNextMatches(e -> e.getType() == StreamEvent.Type.TEXT)
                .expectNextMatches(e -> e.getType() == StreamEvent.Type.DONE)
                .verifyComplete();

        // 状态应已保存
        Optional<WorkflowState> saved = stateStore.load("collab-1");
        assertTrue(saved.isPresent());
        assertEquals(WorkflowState.Status.AWAITING_APPROVAL, saved.get().getStatus());
        assertEquals("工艺确认", saved.get().getApprovalNode());
    }

    @Test
    void shouldApproveCallback() {
        // 先保存状态
        WorkflowState state = WorkflowState.builder()
                .collaborationId("collab-2")
                .approvalId("approval-2")
                .status(WorkflowState.Status.AWAITING_APPROVAL)
                .approvalNode("工艺确认")
                .requestTime(Instant.now())
                .expireTime(Instant.now().plus(Duration.ofHours(24)))
                .build();
        stateStore.save("collab-2", state);

        ApprovalCallback callback = ApprovalCallback.builder()
                .approvalId("approval-2")
                .collaborationId("collab-2")
                .result(ApprovalCallback.Result.APPROVED)
                .comment("同意")
                .approver("admin")
                .build();

        ApprovalGate.ApprovalResult result = approvalGate.handleCallback(callback).block();

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals("审批通过", result.getMessage());

        // 状态应更新为 APPROVED
        Optional<WorkflowState> updated = stateStore.load("collab-2");
        assertTrue(updated.isPresent());
        assertEquals(WorkflowState.Status.APPROVED, updated.get().getStatus());
    }

    @Test
    void shouldRejectCallback() {
        WorkflowState state = WorkflowState.builder()
                .collaborationId("collab-3")
                .approvalId("approval-3")
                .status(WorkflowState.Status.AWAITING_APPROVAL)
                .requestTime(Instant.now())
                .expireTime(Instant.now().plus(Duration.ofHours(24)))
                .build();
        stateStore.save("collab-3", state);

        ApprovalCallback callback = ApprovalCallback.builder()
                .approvalId("approval-3")
                .collaborationId("collab-3")
                .result(ApprovalCallback.Result.REJECTED)
                .comment("方案需要修改")
                .approver("admin")
                .build();

        ApprovalGate.ApprovalResult result = approvalGate.handleCallback(callback).block();

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals("审批驳回", result.getMessage());

        Optional<WorkflowState> updated = stateStore.load("collab-3");
        assertTrue(updated.isPresent());
        assertEquals(WorkflowState.Status.REJECTED, updated.get().getStatus());
    }

    @Test
    void shouldBeIdempotent() {
        // 已处理的审批
        WorkflowState state = WorkflowState.builder()
                .collaborationId("collab-4")
                .approvalId("approval-4")
                .status(WorkflowState.Status.APPROVED)
                .requestTime(Instant.now())
                .expireTime(Instant.now().plus(Duration.ofHours(24)))
                .build();
        stateStore.save("collab-4", state);

        // 重复回调
        ApprovalCallback callback = ApprovalCallback.builder()
                .approvalId("approval-4")
                .collaborationId("collab-4")
                .result(ApprovalCallback.Result.APPROVED)
                .comment("重复")
                .approver("admin")
                .build();

        ApprovalGate.ApprovalResult result = approvalGate.handleCallback(callback).block();

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertTrue(result.isAlreadyProcessed());
        assertEquals("审批已处理，无需重复操作", result.getMessage());
    }

    @Test
    void shouldHandleExpiredState() {
        // 状态已过期
        WorkflowState state = WorkflowState.builder()
                .collaborationId("collab-5")
                .approvalId("approval-5")
                .status(WorkflowState.Status.AWAITING_APPROVAL)
                .requestTime(Instant.now().minus(Duration.ofHours(48)))
                .expireTime(Instant.now().minus(Duration.ofHours(24)))
                .build();
        stateStore.save("collab-5", state);

        ApprovalCallback callback = ApprovalCallback.builder()
                .approvalId("approval-5")
                .collaborationId("collab-5")
                .result(ApprovalCallback.Result.APPROVED)
                .approver("admin")
                .build();

        // load 时会检查过期并删除
        ApprovalGate.ApprovalResult result = approvalGate.handleCallback(callback).block();

        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertEquals("状态不存在或已过期", result.getMessage());
    }
}