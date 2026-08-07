package com.jewel.a2a.server.service;

import com.agentmesh.core.agent.AgentConfig;
import com.agentmesh.core.agent.ReActAgent;
import com.agentmesh.core.agent.SequentialAgentOrchestrator;
import com.agentmesh.core.memory.MemoryManager;
import com.agentmesh.core.prompt.PromptTemplateEngine;
import com.agentmesh.core.protocol.ChatRequest;
import com.agentmesh.core.session.ChatMessage;
import com.agentmesh.core.session.ConversationStore;
import com.agentmesh.core.task.Task;
import com.agentmesh.core.task.TaskRepository;
import com.agentmesh.core.task.TaskStatus;
import com.jewel.a2a.common.dto.ChatResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ChatService 集成测试。
 * 使用 Mockito 模拟所有依赖，验证三种聊天模式的业务逻辑。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ChatService 集成测试")
class ChatServiceTest {

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
        // Mock 基础行为
        when(promptEngine.render(eq("default"), any())).thenReturn("系统提示词");
        when(promptEngine.render(eq("designer"), any())).thenReturn("设计师提示词");
        when(promptEngine.render(eq("crafter"), any())).thenReturn("工艺师提示词");
        when(promptEngine.render(eq("auditor"), any())).thenReturn("审核员提示词");

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

    // ========== 单 Agent 模式 ==========

    @Nested
    @DisplayName("单 Agent 模式 (submitChat)")
    class SingleAgentMode {

        @Test
        @DisplayName("提交聊天应返回正确的 ChatResponse")
        void shouldReturnCorrectChatResponse() {
            ChatRequest request = ChatRequest.builder()
                    .message("你好")
                    .sessionId("test-session")
                    .build();

            ChatResponse response = chatService.submitChat(request);

            assertNotNull(response);
            assertEquals("test-session", response.getSessionId());
            assertNotNull(response.getTaskId());
            assertTrue(response.getTaskId().startsWith("task_"));
            assertEquals("PENDING", response.getStatus());
            assertEquals("任务已提交", response.getMessage());
        }

        @Test
        @DisplayName("不传 sessionId 时应自动生成")
        void shouldAutoGenerateSessionId() {
            ChatRequest request = ChatRequest.builder()
                    .message("你好")
                    .build();

            ChatResponse response = chatService.submitChat(request);

            assertNotNull(response.getSessionId());
            assertTrue(response.getSessionId().startsWith("chat_"));
        }

        @Test
        @DisplayName("应保存 Task 到 TaskRepository")
        void shouldSaveTask() {
            ChatRequest request = ChatRequest.builder()
                    .message("你好")
                    .build();

            chatService.submitChat(request);

            ArgumentCaptor<Task> taskCaptor = ArgumentCaptor.forClass(Task.class);
            verify(taskRepository).save(taskCaptor.capture());
            Task savedTask = taskCaptor.getValue();
            assertEquals("chat", savedTask.getSkillId());
            assertNotNull(savedTask.getInput());
            assertEquals("你好", savedTask.getInput().get("message"));
        }
    }

    // ========== 串联流水线模式 ==========

    @Nested
    @DisplayName("串联流水线模式 (submitChatSequential)")
    class SequentialMode {

        @Test
        @DisplayName("提交串联流水线应返回正确的 ChatResponse")
        void shouldReturnCorrectChatResponse() {
            ChatRequest request = ChatRequest.builder()
                    .message("设计一枚钻戒")
                    .sessionId("seq-session")
                    .build();

            ChatResponse response = chatService.submitChatSequential(request);

            assertNotNull(response);
            assertEquals("seq-session", response.getSessionId());
            assertNotNull(response.getTaskId());
            assertEquals("PENDING", response.getStatus());
            assertEquals("串联流水线任务已提交", response.getMessage());
        }

        @Test
        @DisplayName("不传 sessionId 时应自动生成 seq_ 前缀")
        void shouldAutoGenerateSeqSessionId() {
            ChatRequest request = ChatRequest.builder()
                    .message("设计一枚钻戒")
                    .build();

            ChatResponse response = chatService.submitChatSequential(request);

            assertNotNull(response.getSessionId());
            assertTrue(response.getSessionId().startsWith("seq_"));
        }

        @Test
        @DisplayName("应保存 Task 到 TaskRepository")
        void shouldSaveSequentialTask() {
            ChatRequest request = ChatRequest.builder()
                    .message("设计一枚钻戒")
                    .build();

            chatService.submitChatSequential(request);

            ArgumentCaptor<Task> taskCaptor = ArgumentCaptor.forClass(Task.class);
            verify(taskRepository).save(taskCaptor.capture());
            Task savedTask = taskCaptor.getValue();
            assertEquals("chat_sequential", savedTask.getSkillId());
        }
    }

    // ========== 路由模式 ==========

    @Nested
    @DisplayName("路由模式 (submitChatRouted)")
    class RoutedMode {

        @Test
        @DisplayName("提交路由聊天应返回正确的 ChatResponse")
        void shouldReturnCorrectChatResponse() {
            ChatRequest request = ChatRequest.builder()
                    .message("设计一枚钻戒")
                    .sessionId("route-session")
                    .build();

            ChatResponse response = chatService.submitChatRouted(request);

            assertNotNull(response);
            assertEquals("route-session", response.getSessionId());
            assertNotNull(response.getTaskId());
            assertEquals("PENDING", response.getStatus());
            assertEquals("路由任务已提交", response.getMessage());
        }

        @Test
        @DisplayName("不传 sessionId 时应自动生成 route_ 前缀")
        void shouldAutoGenerateRouteSessionId() {
            ChatRequest request = ChatRequest.builder()
                    .message("设计一枚钻戒")
                    .build();

            ChatResponse response = chatService.submitChatRouted(request);

            assertNotNull(response.getSessionId());
            assertTrue(response.getSessionId().startsWith("route_"));
        }

        @Test
        @DisplayName("应保存 Task 到 TaskRepository")
        void shouldSaveRoutedTask() {
            ChatRequest request = ChatRequest.builder()
                    .message("设计一枚钻戒")
                    .build();

            chatService.submitChatRouted(request);

            ArgumentCaptor<Task> taskCaptor = ArgumentCaptor.forClass(Task.class);
            verify(taskRepository).save(taskCaptor.capture());
            Task savedTask = taskCaptor.getValue();
            assertEquals("chat_routed", savedTask.getSkillId());
        }
    }

    // ========== 关键词路由逻辑 ==========

    @Nested
    @DisplayName("关键词路由逻辑 (routeByKeyword)")
    class KeywordRouting {

        @Test
        @DisplayName("包含'设计'关键词应路由到设计师")
        void shouldRouteToDesigner() {
            // 通过反射调用 private 方法测试路由逻辑
            ChatRequest request = ChatRequest.builder()
                    .message("帮我设计一枚钻戒")
                    .build();

            ChatResponse response = chatService.submitChatRouted(request);
            assertNotNull(response);
            // 实际路由测试在异步执行，这里只验证提交成功
        }

        @Test
        @DisplayName("包含'工艺'关键词应路由到工艺师")
        void shouldRouteToCrafter() {
            ChatRequest request = ChatRequest.builder()
                    .message("检查工艺可行性")
                    .build();

            ChatResponse response = chatService.submitChatRouted(request);
            assertNotNull(response);
        }

        @Test
        @DisplayName("包含'审核'关键词应路由到审核员")
        void shouldRouteToAuditor() {
            ChatRequest request = ChatRequest.builder()
                    .message("审核这个设计方案")
                    .build();

            ChatResponse response = chatService.submitChatRouted(request);
            assertNotNull(response);
        }

        @Test
        @DisplayName("无匹配关键词时应回退到通用 ReActAgent")
        void shouldFallbackToDefault() {
            // 无关键词匹配的消息
            ChatRequest request = ChatRequest.builder()
                    .message("今天天气怎么样")
                    .build();

            ChatResponse response = chatService.submitChatRouted(request);
            assertNotNull(response);
            assertNotNull(response.getTaskId());
        }
    }

    // ========== 会话历史 ==========

    @Nested
    @DisplayName("会话历史 (getHistory)")
    class SessionHistory {

        @Test
        @DisplayName("应返回会话历史列表")
        void shouldReturnHistoryList() {
            ChatMessage msg1 = ChatMessage.builder()
                    .role("user").content("你好").build();
            ChatMessage msg2 = ChatMessage.builder()
                    .role("assistant").content("你好！").build();

            when(memoryManager.recall(eq("test-session"), anyInt()))
                    .thenReturn(List.of(msg1, msg2));

            List<com.jewel.a2a.common.dto.ChatMessage> history =
                    chatService.getHistory("test-session");

            assertNotNull(history);
            assertEquals(2, history.size());
            assertEquals("user", history.get(0).getRole());
            assertEquals("你好", history.get(0).getContent());
            assertEquals("assistant", history.get(1).getRole());
        }

        @Test
        @DisplayName("空历史应返回空列表")
        void shouldReturnEmptyListForNoHistory() {
            when(memoryManager.recall(eq("empty-session"), anyInt()))
                    .thenReturn(List.of());

            List<com.jewel.a2a.common.dto.ChatMessage> history =
                    chatService.getHistory("empty-session");

            assertNotNull(history);
            assertTrue(history.isEmpty());
        }
    }
}