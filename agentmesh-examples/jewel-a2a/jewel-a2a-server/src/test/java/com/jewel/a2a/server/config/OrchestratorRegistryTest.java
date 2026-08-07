package com.jewel.a2a.server.config;

import com.agentmesh.core.agent.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OrchestratorRegistry 单元测试：验证 ExecutionMode 到编排器的映射。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OrchestratorRegistry 单元测试")
class OrchestratorRegistryTest {

    @Mock
    private SequentialAgentOrchestrator sequential;
    @Mock
    private ConditionalOrchestrator conditional;
    @Mock
    private ParallelOrchestrator parallel;
    @Mock
    private DebateOrchestrator debate;
    @Mock
    private SupervisedOrchestrator supervised;
    @Mock
    private SwarmOrchestrator swarm;

    private OrchestratorRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new OrchestratorRegistry(sequential, conditional, parallel, debate, supervised, swarm);
    }

    // ========== 正常映射 ==========

    @Nested
    @DisplayName("正常映射")
    class NormalMapping {

        @Test
        @DisplayName("SEQUENTIAL 应返回 SequentialAgentOrchestrator")
        void shouldMapSequential() {
            assertEquals(sequential, registry.get(ExecutionMode.SEQUENTIAL));
        }

        @Test
        @DisplayName("CONDITIONAL 应返回 ConditionalOrchestrator")
        void shouldMapConditional() {
            assertEquals(conditional, registry.get(ExecutionMode.CONDITIONAL));
        }

        @Test
        @DisplayName("PARALLEL 应返回 ParallelOrchestrator")
        void shouldMapParallel() {
            assertEquals(parallel, registry.get(ExecutionMode.PARALLEL));
        }

        @Test
        @DisplayName("DEBATE 应返回 DebateOrchestrator")
        void shouldMapDebate() {
            assertEquals(debate, registry.get(ExecutionMode.DEBATE));
        }

        @Test
        @DisplayName("SUPERVISED 应返回 SupervisedOrchestrator")
        void shouldMapSupervised() {
            assertEquals(supervised, registry.get(ExecutionMode.SUPERVISED));
        }

        @Test
        @DisplayName("SWARM 应返回 SwarmOrchestrator")
        void shouldMapSwarm() {
            assertEquals(swarm, registry.get(ExecutionMode.SWARM));
        }
    }

    // ========== 异常映射 ==========

    @Nested
    @DisplayName("异常映射")
    class ErrorMapping {

        @Test
        @DisplayName("null mode 应抛出 IllegalArgumentException")
        void shouldThrowForNullMode() {
            assertThrows(IllegalArgumentException.class,
                    () -> registry.get(null));
        }

        @Test
        @DisplayName("异常消息应包含 mode 信息")
        void shouldContainModeInErrorMessage() {
            // 测试未注册的 mode（所有 6 种都已注册，不会触发）
            // 但 null 会触发，验证消息格式
            Exception ex = assertThrows(IllegalArgumentException.class,
                    () -> registry.get(null));
            assertTrue(ex.getMessage().contains("Unsupported"));
        }
    }
}