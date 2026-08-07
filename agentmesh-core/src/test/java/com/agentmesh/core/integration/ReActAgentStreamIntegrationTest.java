package com.agentmesh.core.integration;

import com.agentmesh.core.agent.ReActAgent;
import com.agentmesh.core.llm.OpenAiLlmClient;
import com.agentmesh.core.llm.StreamEvent;
import com.agentmesh.core.llm.adapter.OpenAiAdapter;
import com.agentmesh.core.session.ChatMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 流式 ReActAgent 集成测试：验证 SSE 流式输出全流程。
 */
@DisplayName("ReActAgent 流式集成测试")
class ReActAgentStreamIntegrationTest extends BaseIntegrationTest {

    @Test
    @DisplayName("流式对话：多 chunk 逐块输出，以 DONE 结束")
    void shouldStreamChatResponse() {
        llmMock.stubSequentialStreamResponses(List.of(
                Map.of("type", "chat_stream", "chunks", "你好|！|我是|智能助手")
        ));

        ReActAgent agent = createAgent();
        Flux<StreamEvent> stream = agent.runStream(
                "你是一个智能助手", "你好", List.of());

        StepVerifier.create(stream)
                .expectNextMatches(e -> e.getType() == StreamEvent.Type.THINKING)
                .expectNextMatches(e -> e.getType() == StreamEvent.Type.TEXT && e.getContent().contains("你好"))
                .expectNextMatches(e -> e.getType() == StreamEvent.Type.TEXT)
                .expectNextMatches(e -> e.getType() == StreamEvent.Type.TEXT)
                .expectNextMatches(e -> e.getType() == StreamEvent.Type.TEXT)
                .expectNextMatches(e -> e.getType() == StreamEvent.Type.DONE)
                .verifyComplete();
    }

    @Test
    @DisplayName("流式工具调用：LLM 返回 tool call → Agent 执行 → 继续推理")
    void shouldStreamToolCallAndContinue() {
        llmMock.stubSequentialStreamResponses(List.of(
                Map.of("type", "tool_call_stream", "toolName", "get_weather", "toolArgsJson", "{\"city\":\"北京\"}"),
                Map.of("type", "chat_stream", "chunks", "北京|今天|晴")
        ));

        ReActAgent agent = createAgent();
        Flux<StreamEvent> stream = agent.runStream(
                "你是一个智能助手", "北京天气怎么样", List.of());

        StepVerifier.create(stream)
                .expectNextMatches(e -> e.getType() == StreamEvent.Type.THINKING)
                // 第一轮：tool call
                .expectNextMatches(e -> e.getType() == StreamEvent.Type.TOOL_CALL_START
                        && "get_weather".equals(e.getToolName()))
                .expectNextMatches(e -> e.getType() == StreamEvent.Type.TOOL_CALL_ARGS)
                .expectNextMatches(e -> e.getType() == StreamEvent.Type.TOOL_CALL_END)
                .expectNextMatches(e -> e.getType() == StreamEvent.Type.TOOL_RESULT)
                // 第二轮：文本推理
                .expectNextMatches(e -> e.getType() == StreamEvent.Type.THINKING)
                .expectNextMatches(e -> e.getType() == StreamEvent.Type.TEXT && "北京".equals(e.getContent()))
                .expectNextMatches(e -> e.getType() == StreamEvent.Type.TEXT && "今天".equals(e.getContent()))
                .expectNextMatches(e -> e.getType() == StreamEvent.Type.TEXT && "晴".equals(e.getContent()))
                .expectNextMatches(e -> e.getType() == StreamEvent.Type.DONE)
                .expectNextMatches(e -> e.getType() == StreamEvent.Type.DONE) // 第一轮流 doStream 的 DONE
                .verifyComplete();
    }

    @Test
    @DisplayName("流式错误处理：LLM 返回 HTTP 500 → 返回 ERROR 事件后结束")
    void shouldHandleStreamError() {
        llmMock.stubErrorResponse(500, "Internal Server Error");

        ReActAgent agent = createAgent();
        Flux<StreamEvent> stream = agent.runStream(
                "你是一个智能助手", "测试错误", List.of());

        StepVerifier.create(stream)
                .expectNextMatches(e -> e.getType() == StreamEvent.Type.THINKING)
                .expectNextMatches(e -> e.getType() == StreamEvent.Type.ERROR)
                .verifyComplete();
    }

    // ========== 辅助方法 ==========

    private ReActAgent createAgent() {
        var llmClient = new OpenAiLlmClient(
                "test-key", llmMock.baseUrl(), "test-model",
                new OpenAiAdapter(), null);
        return new ReActAgent(llmClient, toolRegistry, 5);
    }
}