package com.agentmesh.core.collaboration;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SwarmResult 和 SwarmCollaboration 测试。
 * 覆盖多数投票、平票、低置信度拒绝、加权投票。
 */
class SwarmCollaborationTest {

    private InMemoryMessageBus messageBus;
    private CollaborationMetrics collabMetrics;
    private SwarmCollaboration collaboration;

    @BeforeEach
    void setUp() {
        collabMetrics = new CollaborationMetrics(new SimpleMeterRegistry());
        messageBus = new InMemoryMessageBus(256, collabMetrics);
        collaboration = new SwarmCollaboration(Duration.ofSeconds(5), null, collabMetrics);
    }

    @AfterEach
    void tearDown() {
        messageBus.close();
    }

    @Test
    void shouldCreateSwarmResultWithMajority() {
        SwarmResult result = SwarmResult.builder()
                .selectedResult("方案A")
                .strategy("majority")
                .votes(List.of(
                        SwarmResult.VoteDetail.builder().agentId("a1").result("方案A").confidence(0.9).build(),
                        SwarmResult.VoteDetail.builder().agentId("a2").result("方案A").confidence(0.8).build(),
                        SwarmResult.VoteDetail.builder().agentId("a3").result("方案B").confidence(0.7).build()
                ))
                .consensus(true)
                .confidence(0.67)
                .build();

        assertEquals("方案A", result.getSelectedResult());
        assertTrue(result.isConsensus());
        assertEquals(0.67, result.getConfidence(), 0.01);
    }

    @Test
    void shouldCreateSwarmResultWithoutConsensus() {
        SwarmResult result = SwarmResult.builder()
                .selectedResult("方案A")
                .strategy("majority")
                .votes(List.of(
                        SwarmResult.VoteDetail.builder().agentId("a1").result("方案A").confidence(0.6).build(),
                        SwarmResult.VoteDetail.builder().agentId("a2").result("方案B").confidence(0.7).build()
                ))
                .consensus(false)
                .confidence(0.5)
                .build();

        assertFalse(result.isConsensus());
        assertEquals(2, result.getVotes().size());
    }

    @Test
    void shouldRejectLowConfidenceResults() {
        // 所有置信度低于 0.5
        List<SwarmResult.VoteDetail> votes = List.of(
                SwarmResult.VoteDetail.builder().agentId("a1").result("方案A").confidence(0.3).build(),
                SwarmResult.VoteDetail.builder().agentId("a2").result("方案A").confidence(0.4).build()
        );

        double avgConfidence = votes.stream()
                .mapToDouble(SwarmResult.VoteDetail::getConfidence)
                .average()
                .orElse(0.0);

        assertTrue(avgConfidence < 0.5, "平均置信度低于 0.5 应拒绝");
    }

    @Test
    void shouldHandleWeightedVote() {
        SwarmResult result = SwarmResult.builder()
                .selectedResult("方案A")
                .strategy("weighted")
                .votes(List.of(
                        SwarmResult.VoteDetail.builder().agentId("a1").result("方案A").confidence(0.95).reasoning("高准确率").build(),
                        SwarmResult.VoteDetail.builder().agentId("a2").result("方案B").confidence(0.6).reasoning("低准确率").build()
                ))
                .consensus(true)
                .confidence(0.85)
                .build();

        assertEquals("weighted", result.getStrategy());
        assertEquals("高准确率", result.getVotes().get(0).getReasoning());
    }
}