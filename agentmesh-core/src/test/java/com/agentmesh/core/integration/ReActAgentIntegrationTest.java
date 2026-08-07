package com.agentmesh.core.integration;

import com.agentmesh.core.agent.ReActAgent;
import com.agentmesh.core.llm.OpenAiLlmClient;
import com.agentmesh.core.llm.adapter.OpenAiAdapter;
import com.agentmesh.core.session.ChatMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ReActAgent 集成测试：使用 WireMock 模拟 LLM，验证完整推理循环。
 */
@DisplayName("ReActAgent 集成测试")
class ReActAgentIntegrationTest extends BaseIntegrationTest {

    @Test
    @DisplayName("普通对话：LLM 直接回复，不调用工具")
    void shouldChatWithoutToolCall() {
        llmMock.stubSequentialResponses(List.of(
                Map.of("type", "chat", "content", "你好！我是智能助手，有什么可以帮你的？")
        ));

        ReActAgent agent = createAgent();
        ReActAgent.AgentResult result = agent.run(
                "你是一个智能助手", "你好", List.of());

        assertThat(result.reply).contains("智能助手");
        assertThat(result.toolCalls).isEmpty();
    }

    @Test
    @DisplayName("工具调用：LLM 返回 function call，Agent 执行工具并返回结果")
    void shouldExecuteToolAndReturnResult() {
        // 第一轮：LLM 返回 tool call
        // 第二轮：LLM 返回最终回复
        llmMock.stubSequentialResponses(List.of(
                Map.of("type", "tool_call", "toolName", "get_weather", "toolArgsJson", "{\"city\":\"北京\"}"),
                Map.of("type", "chat", "content", "北京今天天气晴朗，温度 25°C")
        ));

        ReActAgent agent = createAgent();
        ReActAgent.AgentResult result = agent.run(
                "你是一个智能助手", "北京天气怎么样", List.of());

        assertThat(result.reply).contains("25°C");
        assertThat(result.toolCalls).hasSize(1);
        assertThat(result.toolCalls.get(0).get("tool")).isEqualTo("get_weather");
    }

    @Test
    @DisplayName("多轮工具调用：Agent 连续调用多个工具")
    void shouldHandleMultipleToolCalls() {
        // 第一轮：搜索知识库
        // 第二轮：查天气
        // 第三轮：最终回复
        llmMock.stubSequentialResponses(List.of(
                Map.of("type", "tool_call", "toolName", "search_knowledge", "toolArgsJson", "{\"query\":\"AgentMesh\"}"),
                Map.of("type", "tool_call", "toolName", "get_weather", "toolArgsJson", "{\"city\":\"上海\"}"),
                Map.of("type", "chat", "content", "根据查询结果，AgentMesh 是一个多 Agent 框架，上海天气晴朗")
        ));

        ReActAgent agent = createAgent();
        ReActAgent.AgentResult result = agent.run(
                "你是一个智能助手", "帮我查一下 AgentMesh 和上海天气", List.of());

        assertThat(result.reply).contains("AgentMesh").contains("上海");
        assertThat(result.toolCalls).hasSize(2);
    }

    @Test
    @DisplayName("带历史记录：Agent 能正确合并历史消息")
    void shouldIncludeHistoryInContext() {
        llmMock.stubSequentialResponses(List.of(
                Map.of("type", "chat", "content", "好的，我记住了，你之前问过天气")
        ));

        ReActAgent agent = createAgent();
        List<ChatMessage> history = List.of(
                ChatMessage.builder().role("user").content("之前的问题").build(),
                ChatMessage.builder().role("assistant").content("之前的回答").build()
        );

        ReActAgent.AgentResult result = agent.run(
                "你是一个智能助手", "新问题", history);

        assertThat(result.reply).isNotEmpty();
    }

    @Test
    @DisplayName("超限终止：达到 maxLoops 后终止并返回友好提示")
    void shouldStopWhenMaxLoopsReached() {
        // 持续返回 tool call，触发 maxLoops=2
        List<Map<String, String>> responses = new java.util.ArrayList<>();
        for (int i = 0; i < 5; i++) {
            responses.add(Map.of("type", "tool_call", "toolName", "get_weather", "toolArgsJson", "{\"city\":\"北京\"}"));
        }
        llmMock.stubSequentialResponses(responses);

        ReActAgent agent = createAgentWithMaxLoops(2);
        ReActAgent.AgentResult result = agent.run(
                "你是一个智能助手", "查天气", List.of());

        assertThat(result.reply).contains("抱歉");
    }

    // ========== 辅助方法 ==========

    private ReActAgent createAgent() {
        var llmClient = new OpenAiLlmClient(
                "test-key", llmMock.baseUrl(), "test-model",
                new OpenAiAdapter(), null);
        return new ReActAgent(llmClient, toolRegistry, 5);
    }

    private ReActAgent createAgentWithMaxLoops(int maxLoops) {
        var llmClient = new OpenAiLlmClient(
                "test-key", llmMock.baseUrl(), "test-model",
                new OpenAiAdapter(), null);
        return new ReActAgent(llmClient, toolRegistry, maxLoops);
    }
}