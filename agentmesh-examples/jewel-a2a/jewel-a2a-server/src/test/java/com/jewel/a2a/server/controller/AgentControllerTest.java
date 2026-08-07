package com.jewel.a2a.server.controller;

import com.agentmesh.core.protocol.ChatRequest;
import com.agentmesh.core.registry.AgentNode;
import com.agentmesh.core.registry.AgentRegistry;
import com.jewel.a2a.common.dto.ChatResponse;
import com.jewel.a2a.common.enums.TaskStatus;
import com.jewel.a2a.repository.entity.TaskEntity;
import com.jewel.a2a.server.service.ChatService;
import com.jewel.a2a.server.service.SseEmitterService;
import com.jewel.a2a.server.service.TaskService;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * AgentController 独立 MockMvc 测试：验证所有 HTTP 端点的请求/响应。
 * 使用 standaloneSetup 避免 Spring 容器依赖。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AgentController MockMvc 测试")
class AgentControllerTest {

    @Mock
    private AgentRegistry agentRegistry;

    @Mock
    private TaskService taskService;

    @Mock
    private SseEmitterService sseEmitterService;

    @Mock
    private ChatService chatService;

    @InjectMocks
    private AgentController agentController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(agentController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ========== GET /.well-known/agent.json ==========

    @Nested
    @DisplayName("Agent 名片")
    class AgentCard {

        @BeforeEach
        void setUp() {
            AgentNode node = mock(AgentNode.class);
            when(node.getCard()).thenReturn(com.agentmesh.core.protocol.AgentCard.builder()
                    .agentId("jewel-a2a")
                    .name("Jewel-A2A")
                    .version("2.0.0")
                    .skills(List.of())
                    .build());
            when(agentRegistry.resolve("jewel-a2a")).thenReturn(node);
        }

        @Test
        @DisplayName("应返回 200 和 JSON")
        void shouldReturn200AndJson() throws Exception {
            mockMvc.perform(get("/.well-known/agent.json"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.agentId").value("jewel-a2a"))
                    .andExpect(jsonPath("$.name").value("Jewel-A2A"));
        }

        @Test
        @DisplayName("Agent 未注册时应返回 500")
        void shouldReturn500WhenAgentNotRegistered() throws Exception {
            when(agentRegistry.resolve("jewel-a2a")).thenReturn(null);

            mockMvc.perform(get("/.well-known/agent.json"))
                    .andExpect(status().isInternalServerError());
        }
    }

    // ========== POST /a2a/run ==========

    @Nested
    @DisplayName("任务执行")
    class RunTask {

        @Test
        @DisplayName("应返回 200 和 TaskResponse")
        void shouldReturnTaskResponse() throws Exception {
            when(taskService.submitTask(any()))
                    .thenReturn(com.jewel.a2a.common.dto.TaskResponse.builder()
                            .taskId("task-abc123")
                            .status(TaskStatus.PENDING)
                            .message("任务已提交")
                            .build());

            mockMvc.perform(post("/a2a/run")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"skillId\":\"chat\",\"input\":{\"message\":\"hello\"}}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.taskId").value("task-abc123"));
        }
    }

    // ========== POST /a2a/chat ==========

    @Nested
    @DisplayName("聊天接口")
    class Chat {

        @Test
        @DisplayName("单 Agent 聊天应返回 200")
        void shouldReturnChatResponse() throws Exception {
            when(chatService.submitChat(any(ChatRequest.class)))
                    .thenReturn(ChatResponse.builder()
                            .taskId("task-1")
                            .sessionId("chat-1")
                            .status("PENDING")
                            .message("任务已提交")
                            .build());

            mockMvc.perform(post("/a2a/chat")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"message\":\"你好\",\"sessionId\":\"test\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.taskId").value("task-1"));
        }
    }

    // ========== POST /a2a/chat/sequential ==========

    @Nested
    @DisplayName("串联流水线")
    class Sequential {

        @Test
        @DisplayName("应返回 200")
        void shouldReturn200() throws Exception {
            when(chatService.submitChatSequential(any(ChatRequest.class)))
                    .thenReturn(ChatResponse.builder()
                            .taskId("task-seq")
                            .sessionId("seq-1")
                            .status("PENDING")
                            .build());

            mockMvc.perform(post("/a2a/chat/sequential")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"message\":\"设计一枚钻戒\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.taskId").value("task-seq"));
        }
    }

    // ========== POST /a2a/chat/routed ==========

    @Nested
    @DisplayName("路由聊天")
    class Routed {

        @Test
        @DisplayName("应返回 200")
        void shouldReturn200() throws Exception {
            when(chatService.submitChatRouted(any(ChatRequest.class)))
                    .thenReturn(ChatResponse.builder()
                            .taskId("task-route")
                            .sessionId("route-1")
                            .status("PENDING")
                            .build());

            mockMvc.perform(post("/a2a/chat/routed")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"message\":\"审核这个方案\"}"))
                    .andExpect(status().isOk());
        }
    }

    // ========== POST /a2a/chat/stream ==========

    @Nested
    @DisplayName("流式聊天")
    class Stream {

        @Test
        @DisplayName("应返回 200")
        void shouldReturn200() throws Exception {
            when(chatService.submitChatStream(any(ChatRequest.class)))
                    .thenReturn(ChatResponse.builder()
                            .taskId("task-stream")
                            .status("PENDING")
                            .build());

            mockMvc.perform(post("/a2a/chat/stream")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"message\":\"生成设计\"}"))
                    .andExpect(status().isOk());
        }
    }

    // ========== 高级编排端点 ==========

    @Nested
    @DisplayName("高级编排")
    class AdvancedOrchestration {

        @Test
        @DisplayName("POST /a2a/chat/conditional 应返回 200")
        void shouldReturn200ForConditional() throws Exception {
            when(chatService.submitChatConditional(any(ChatRequest.class)))
                    .thenReturn(ChatResponse.builder().taskId("task-cond").status("PENDING").build());

            mockMvc.perform(post("/a2a/chat/conditional")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"message\":\"test\"}"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("POST /a2a/chat/parallel 应返回 200")
        void shouldReturn200ForParallel() throws Exception {
            when(chatService.submitChatParallel(any(ChatRequest.class)))
                    .thenReturn(ChatResponse.builder().taskId("task-par").status("PENDING").build());

            mockMvc.perform(post("/a2a/chat/parallel")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"message\":\"test\"}"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("POST /a2a/chat/debate 应返回 200")
        void shouldReturn200ForDebate() throws Exception {
            when(chatService.submitChatDebate(any(ChatRequest.class)))
                    .thenReturn(ChatResponse.builder().taskId("task-deb").status("PENDING").build());

            mockMvc.perform(post("/a2a/chat/debate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"message\":\"test\"}"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("POST /a2a/chat/supervised 应返回 200")
        void shouldReturn200ForSupervised() throws Exception {
            when(chatService.submitChatSupervised(any(ChatRequest.class)))
                    .thenReturn(ChatResponse.builder().taskId("task-sup").status("PENDING").build());

            mockMvc.perform(post("/a2a/chat/supervised")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"message\":\"test\"}"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("POST /a2a/chat/swarm 应返回 200")
        void shouldReturn200ForSwarm() throws Exception {
            when(chatService.submitChatSwarm(any(ChatRequest.class)))
                    .thenReturn(ChatResponse.builder().taskId("task-swarm").status("PENDING").build());

            mockMvc.perform(post("/a2a/chat/swarm")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"message\":\"test\"}"))
                    .andExpect(status().isOk());
        }
    }

    // ========== GET /a2a/chat/{sessionId} ==========

    @Nested
    @DisplayName("会话历史")
    class ChatHistory {

        @Test
        @DisplayName("应返回 200 和消息列表")
        void shouldReturn200WithMessages() throws Exception {
            when(chatService.getHistory("test-session"))
                    .thenReturn(List.of(
                            com.jewel.a2a.common.dto.ChatMessage.builder()
                                    .role("user").content("你好").build(),
                            com.jewel.a2a.common.dto.ChatMessage.builder()
                                    .role("assistant").content("你好！").build()
                    ));

            mockMvc.perform(get("/a2a/chat/test-session"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.sessionId").value("test-session"))
                    .andExpect(jsonPath("$.messages[0].role").value("user"));
        }
    }

    // ========== GET /a2a/result/{taskId} ==========

    @Nested
    @DisplayName("SSE 结果订阅")
    class SseResult {

        @Test
        @DisplayName("应返回 200")
        void shouldReturnSse() throws Exception {
            when(sseEmitterService.createEmitter("task-1"))
                    .thenReturn(new SseEmitter(120_000L));

            mockMvc.perform(get("/a2a/result/task-1"))
                    .andExpect(status().isOk());
        }
    }

    // ========== GET /a2a/task/{taskId} ==========

    @Nested
    @DisplayName("任务轮询")
    class TaskQuery {

        @Test
        @DisplayName("应返回 200 和 TaskEvent")
        void shouldReturnTaskEvent() throws Exception {
            TaskEntity entity = TaskEntity.builder()
                    .taskId("task-1")
                    .status(TaskStatus.SUCCESS)
                    .output(Map.of("reply", "完成"))
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            when(taskService.getTask("task-1")).thenReturn(entity);

            mockMvc.perform(get("/a2a/task/task-1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.taskId").value("task-1"))
                    .andExpect(jsonPath("$.status").value("SUCCESS"));
        }

        @Test
        @DisplayName("不存在的任务应返回 404")
        void shouldReturn404ForUnknownTask() throws Exception {
            when(taskService.getTask("unknown"))
                    .thenThrow(new com.jewel.a2a.common.exception.A2AException(404, "任务不存在: unknown"));

            mockMvc.perform(get("/a2a/task/unknown"))
                    .andExpect(status().isNotFound());
        }
    }

    // ========== 会话管理 ==========

    @Nested
    @DisplayName("会话管理")
    class ConversationManagement {

        @Test
        @DisplayName("GET /a2a/conversations 应返回 200")
        void shouldReturnConversationList() throws Exception {
            when(chatService.listConversations()).thenReturn(List.of(
                    Map.of("sessionId", "s1", "messageCount", 5),
                    Map.of("sessionId", "s2", "messageCount", 3)
            ));

            mockMvc.perform(get("/a2a/conversations"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].sessionId").value("s1"));
        }

        @Test
        @DisplayName("DELETE /a2a/conversations/{sessionId} 应返回 200")
        void shouldDeleteConversation() throws Exception {
            when(chatService.deleteConversation("s1")).thenReturn(true);

            mockMvc.perform(delete("/a2a/conversations/s1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("删除不存在的会话应返回 success=false")
        void shouldReturnFalseForUnknownSession() throws Exception {
            when(chatService.deleteConversation("unknown")).thenReturn(false);

            mockMvc.perform(delete("/a2a/conversations/unknown"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(false));
        }
    }
}