package com.jewel.a2a.server.boundary;

import com.agentmesh.core.agent.AgentConfig;
import com.agentmesh.core.agent.ReActAgent;
import com.agentmesh.core.agent.SequentialAgentOrchestrator;
import com.agentmesh.core.memory.MemoryManager;
import com.agentmesh.core.prompt.PromptTemplateEngine;
import com.agentmesh.core.protocol.ChatRequest;
import com.agentmesh.core.session.ChatMessage;
import com.agentmesh.core.session.ConversationStore;
import com.agentmesh.core.task.TaskRepository;
import com.jewel.a2a.server.service.AgentMeshConversationStore;
import com.jewel.a2a.server.service.ChatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 边界测试：空输入、null 参数、异常输入等边界条件。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("边界测试")
class BoundaryTest {

    @Mock
    private ConversationStore conversationStore;
    @Mock
    private TaskRepository taskRepository;
    @Mock
    private ReActAgent reActAgent;
    @Mock
    private AgentMeshConversationStore persistenceStore;
    @Mock
    private PromptTemplateEngine promptEngine;
    @Mock
    private MemoryManager memoryManager;
    @Mock
    private SequentialAgentOrchestrator sequentialOrchestrator;
    @Mock
    private AgentConfig designerAgent;
    @Mock
    private AgentConfig crafterAgent;
    @Mock
    private AgentConfig auditorAgent;

    @InjectMocks
    private ChatService chatService;

    @BeforeEach
    void setUp() {
        when(promptEngine.render(anyString(), any())).thenReturn("系统提示词");
        ReActAgent.AgentResult mockResult = new ReActAgent.AgentResult();
        mockResult.reply = "模拟回复";
        mockResult.toolCalls = List.of();
        when(reActAgent.run(anyString(), anyString(), anyList())).thenReturn(mockResult);
        when(memoryManager.recall(anyString(), anyInt())).thenReturn(List.of());

        // AgentConfig Mock
        when(designerAgent.getAgentId()).thenReturn("designer");
        when(designerAgent.getPromptTemplate()).thenReturn("designer");
        when(designerAgent.getLlmClient()).thenReturn(null);
        when(designerAgent.getToolRegistry()).thenReturn(null);
        when(designerAgent.getMaxLoops()).thenReturn(5);
        when(designerAgent.getRoutingTags()).thenReturn(List.of("设计", "画图", "戒指"));

        when(crafterAgent.getAgentId()).thenReturn("crafter");
        when(crafterAgent.getPromptTemplate()).thenReturn("crafter");
        when(crafterAgent.getLlmClient()).thenReturn(null);
        when(crafterAgent.getToolRegistry()).thenReturn(null);
        when(crafterAgent.getMaxLoops()).thenReturn(5);
        when(crafterAgent.getRoutingTags()).thenReturn(List.of("工艺", "材质", "金属"));

        when(auditorAgent.getAgentId()).thenReturn("auditor");
        when(auditorAgent.getPromptTemplate()).thenReturn("auditor");
        when(auditorAgent.getLlmClient()).thenReturn(null);
        when(auditorAgent.getToolRegistry()).thenReturn(null);
        when(auditorAgent.getMaxLoops()).thenReturn(3);
        when(auditorAgent.getRoutingTags()).thenReturn(List.of("审核", "检查", "评审"));
    }

    // ========== 空输入 / null 测试 ==========

    @Nested
    @DisplayName("空输入和 null 参数")
    class EmptyAndNullInput {

        @Test
        @DisplayName("空消息应正常处理（单 Agent 模式）")
        void shouldHandleEmptyMessage() {
            ChatRequest request = ChatRequest.builder()
                    .message("")
                    .build();

            var response = chatService.submitChat(request);
            assertNotNull(response);
            assertNotNull(response.getTaskId());
            assertNotNull(response.getSessionId());
        }

        @Test
        @DisplayName("null 消息应正常处理（单 Agent 模式）")
        void shouldHandleNullMessage() {
            ChatRequest request = ChatRequest.builder()
                    .message(null)
                    .build();

            var response = chatService.submitChat(request);
            assertNotNull(response);
            assertNotNull(response.getTaskId());
        }

        @Test
        @DisplayName("空消息应正常处理（串联模式）")
        void shouldHandleEmptyMessageSequential() {
            ChatRequest request = ChatRequest.builder()
                    .message("")
                    .build();

            var response = chatService.submitChatSequential(request);
            assertNotNull(response);
            assertNotNull(response.getTaskId());
        }

        @Test
        @DisplayName("空消息应正常处理（路由模式）")
        void shouldHandleEmptyMessageRouted() {
            ChatRequest request = ChatRequest.builder()
                    .message("")
                    .build();

            var response = chatService.submitChatRouted(request);
            assertNotNull(response);
            assertNotNull(response.getTaskId());
        }

        @Test
        @DisplayName("null sessionId 应自动生成（单 Agent 模式）")
        void shouldAutoGenerateSessionIdForNull() {
            ChatRequest request = ChatRequest.builder()
                    .message("测试")
                    .sessionId(null)
                    .build();

            var response = chatService.submitChat(request);
            assertNotNull(response.getSessionId());
            assertTrue(response.getSessionId().startsWith("chat_"));
        }

        @Test
        @DisplayName("null sessionId 应自动生成（串联模式）")
        void shouldAutoGenerateSeqSessionIdForNull() {
            ChatRequest request = ChatRequest.builder()
                    .message("测试")
                    .sessionId(null)
                    .build();

            var response = chatService.submitChatSequential(request);
            assertNotNull(response.getSessionId());
            assertTrue(response.getSessionId().startsWith("seq_"));
        }

        @Test
        @DisplayName("null sessionId 应自动生成（路由模式）")
        void shouldAutoGenerateRouteSessionIdForNull() {
            ChatRequest request = ChatRequest.builder()
                    .message("测试")
                    .sessionId(null)
                    .build();

            var response = chatService.submitChatRouted(request);
            assertNotNull(response.getSessionId());
            assertTrue(response.getSessionId().startsWith("route_"));
        }
    }

    // ========== 路由无匹配测试 ==========

    @Nested
    @DisplayName("路由无匹配场景")
    class RoutingNoMatch {

        @Test
        @DisplayName("无关键词匹配时应正常回退")
        void shouldFallbackOnNoKeywordMatch() {
            ChatRequest request = ChatRequest.builder()
                    .message("xyz123 无意义文本")
                    .build();

            var response = chatService.submitChatRouted(request);
            assertNotNull(response);
            assertEquals("PENDING", response.getStatus());
        }

        @Test
        @DisplayName("纯英文消息应能正常路由")
        void shouldHandleEnglishMessage() {
            ChatRequest request = ChatRequest.builder()
                    .message("Hello, how are you?")
                    .build();

            var response = chatService.submitChatRouted(request);
            assertNotNull(response);
        }

        @Test
        @DisplayName("超长消息应能正常处理")
        void shouldHandleVeryLongMessage() {
            String longMessage = "设计".repeat(1000);
            ChatRequest request = ChatRequest.builder()
                    .message(longMessage)
                    .build();

            var response = chatService.submitChatRouted(request);
            assertNotNull(response);
        }

        @Test
        @DisplayName("特殊字符消息应能正常处理")
        void shouldHandleSpecialCharacters() {
            ChatRequest request = ChatRequest.builder()
                    .message("!@#$%^&*()_+-=[]{}|;':\",./<>?")
                    .build();

            var response = chatService.submitChatRouted(request);
            assertNotNull(response);
        }
    }

    // ========== ChatResponse 字段完整性 ==========

    @Nested
    @DisplayName("ChatResponse 字段完整性")
    class ChatResponseIntegrity {

        @Test
        @DisplayName("单 Agent 模式响应应包含所有必要字段")
        void singleAgentResponseShouldHaveAllFields() {
            ChatRequest request = ChatRequest.builder()
                    .message("测试")
                    .sessionId("my-session")
                    .build();

            var response = chatService.submitChat(request);

            assertNotNull(response.getSessionId());
            assertNotNull(response.getTaskId());
            assertNotNull(response.getStatus());
            assertNotNull(response.getMessage());
            assertEquals("my-session", response.getSessionId());
            assertEquals("PENDING", response.getStatus());
        }

        @Test
        @DisplayName("串联模式响应应包含所有必要字段")
        void sequentialResponseShouldHaveAllFields() {
            ChatRequest request = ChatRequest.builder()
                    .message("测试")
                    .sessionId("my-session")
                    .build();

            var response = chatService.submitChatSequential(request);

            assertNotNull(response.getSessionId());
            assertNotNull(response.getTaskId());
            assertNotNull(response.getStatus());
            assertNotNull(response.getMessage());
        }

        @Test
        @DisplayName("路由模式响应应包含所有必要字段")
        void routedResponseShouldHaveAllFields() {
            ChatRequest request = ChatRequest.builder()
                    .message("测试")
                    .sessionId("my-session")
                    .build();

            var response = chatService.submitChatRouted(request);

            assertNotNull(response.getSessionId());
            assertNotNull(response.getTaskId());
            assertNotNull(response.getStatus());
            assertNotNull(response.getMessage());
        }

        @Test
        @DisplayName("taskId 应以 task_ 开头")
        void taskIdShouldStartWithTaskPrefix() {
            ChatRequest request = ChatRequest.builder()
                    .message("测试")
                    .build();

            var response1 = chatService.submitChat(request);
            var response2 = chatService.submitChatSequential(request);
            var response3 = chatService.submitChatRouted(request);

            assertTrue(response1.getTaskId().startsWith("task_"));
            assertTrue(response2.getTaskId().startsWith("task_"));
            assertTrue(response3.getTaskId().startsWith("task_"));
        }

        @Test
        @DisplayName("每次提交应生成不同的 taskId")
        void shouldGenerateUniqueTaskIds() {
            ChatRequest request = ChatRequest.builder()
                    .message("测试")
                    .build();

            var response1 = chatService.submitChat(request);
            var response2 = chatService.submitChat(request);

            assertNotEquals(response1.getTaskId(), response2.getTaskId());
        }
    }

    // ========== 会话历史边界测试 ==========

    @Nested
    @DisplayName("会话历史边界")
    class HistoryBoundary {

        @Test
        @DisplayName("不存在的 sessionId 应返回空列表")
        void shouldReturnEmptyForUnknownSession() {
            when(memoryManager.recall(eq("unknown"), anyInt())).thenReturn(List.of());

            var history = chatService.getHistory("unknown");
            assertNotNull(history);
            assertTrue(history.isEmpty());
        }

        @Test
        @DisplayName("null sessionId 应返回空列表")
        void shouldReturnEmptyForNullSession() {
            when(memoryManager.recall(isNull(), anyInt())).thenReturn(List.of());

            var history = chatService.getHistory(null);
            assertNotNull(history);
            assertTrue(history.isEmpty());
        }
    }
}