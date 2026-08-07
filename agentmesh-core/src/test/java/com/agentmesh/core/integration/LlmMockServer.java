package com.agentmesh.core.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.stubbing.Scenario;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

/**
 * WireMock 模拟 LLM API（OpenAI 兼容格式）。
 * 支持普通 chat 和 function calling 两种响应，以及多轮顺序响应。
 */
public class LlmMockServer {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final WireMockServer server;
    private final AtomicInteger scenarioCounter = new AtomicInteger(0);

    public LlmMockServer() {
        this.server = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
    }

    public void start() {
        server.start();
        WireMock.configureFor(server.port());
    }

    public void stop() {
        server.stop();
    }

    public int port() {
        return server.port();
    }

    public String baseUrl() {
        return "http://localhost:" + server.port();
    }

    // ========== 顺序响应：支持多轮 LLM 调用 ==========

    /**
     * 设置多轮顺序响应，每轮调用依次返回不同的响应。
     * 适用于 ReAct 循环中多轮 LLM 调用的场景。
     *
     * @param responses 响应列表，每个元素是 (type, ...)：
     *                  type="chat" → (content)
     *                  type="tool_call" → (toolName, toolArgsJson)
     */
    public void stubSequentialResponses(List<Map<String, String>> responses) {
        String scenario = "seq-" + scenarioCounter.incrementAndGet();
        for (int i = 0; i < responses.size(); i++) {
            Map<String, String> resp = responses.get(i);
            String state = i == 0 ? Scenario.STARTED : "step-" + i;
            String nextState = i < responses.size() - 1 ? "step-" + (i + 1) : null;

            String body;
            if ("tool_call".equals(resp.get("type"))) {
                body = toolCallResponseBody(resp.get("toolName"), resp.get("toolArgsJson"));
            } else {
                body = jsonResponse(resp.getOrDefault("content", ""), "stop");
            }

            var builder = post(urlPathEqualTo("/chat/completions"))
                    .inScenario(scenario)
                    .whenScenarioStateIs(state)
                    .willReturn(aResponse()
                            .withHeader("Content-Type", "application/json")
                            .withBody(body));

            if (nextState != null) {
                builder.willSetStateTo(nextState);
            }

            stubFor(builder);
        }
    }

    // ========== 普通 Chat 响应 ==========

    public void stubChatResponse(String responseContent) {
        stubFor(post(urlPathEqualTo("/chat/completions"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(jsonResponse(responseContent, "stop"))));
    }

    // ========== Function Calling 响应 ==========

    public void stubToolCallResponse(String toolName, String toolArgsJson) {
        stubFor(post(urlPathEqualTo("/chat/completions"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(toolCallResponseBody(toolName, toolArgsJson))));
    }

    // ========== 流式 Chat 响应 (SSE) ==========

    public void stubChatStreamResponse(String... chunks) {
        StringBuilder sse = new StringBuilder();
        for (int i = 0; i < chunks.length; i++) {
            sse.append("data: ").append(jsonChunk(chunks[i], i == chunks.length - 1)).append("\n\n");
        }
        sse.append("data: [DONE]\n\n");

        stubFor(post(urlPathEqualTo("/chat/completions"))
                .withRequestBody(containing("\"stream\":true"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "text/event-stream")
                        .withBody(sse.toString())));
    }

    // ========== 流式 Tool Call 响应 (SSE) ==========

    public void stubToolCallStreamResponse(String toolName, String toolArgsJson) {
        String sse = String.format("""
                data: {"id":"chatcmpl-test","object":"chat.completion.chunk","choices":[{"index":0,"delta":{"role":"assistant","content":null,"tool_calls":[{"index":0,"id":"call_test_001","type":"function","function":{"name":"%s","arguments":""}}]}}]}
                
                data: {"id":"chatcmpl-test","object":"chat.completion.chunk","choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"function":{"arguments":"%s"}}]}}]}
                
                data: {"id":"chatcmpl-test","object":"chat.completion.chunk","choices":[{"index":0,"delta":{},"finish_reason":"tool_calls"}]}
                
                data: [DONE]
                
                """, toolName, escapeJson(toolArgsJson));

        stubFor(post(urlPathEqualTo("/chat/completions"))
                .withRequestBody(containing("\"stream\":true"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "text/event-stream")
                        .withBody(sse)));
    }

    /**
     * 设置流式顺序响应，支持多轮 SSE 流式调用。
     */
    public void stubSequentialStreamResponses(List<Map<String, String>> responses) {
        String scenario = "stream-seq-" + scenarioCounter.incrementAndGet();
        for (int i = 0; i < responses.size(); i++) {
            Map<String, String> resp = responses.get(i);
            String state = i == 0 ? Scenario.STARTED : "step-" + i;
            String nextState = i < responses.size() - 1 ? "step-" + (i + 1) : null;

            String sseBody;
            if ("tool_call_stream".equals(resp.get("type"))) {
                sseBody = toolCallStreamBody(resp.get("toolName"), resp.get("toolArgsJson"));
            } else {
                // chat stream
                String[] chunks = resp.getOrDefault("chunks", "").split("\\|");
                sseBody = chatStreamBody(chunks);
            }

            var builder = post(urlPathEqualTo("/chat/completions"))
                    .withRequestBody(containing("\"stream\":true"))
                    .inScenario(scenario)
                    .whenScenarioStateIs(state)
                    .willReturn(aResponse()
                            .withHeader("Content-Type", "text/event-stream")
                            .withBody(sseBody));

            if (nextState != null) {
                builder.willSetStateTo(nextState);
            }

            stubFor(builder);
        }
    }

    // ========== 错误响应 ==========

    public void stubErrorResponse(int httpStatus, String errorMessage) {
        stubFor(post(urlPathEqualTo("/chat/completions"))
                .willReturn(aResponse()
                        .withStatus(httpStatus)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":{\"message\":\"" + errorMessage + "\"}}")));
    }

    public void resetAll() {
        server.resetAll();
    }

    // ========== 私有方法 ==========

    private String toolCallResponseBody(String toolName, String toolArgsJson) {
        return String.format("""
                {
                  "id": "chatcmpl-test",
                  "object": "chat.completion",
                  "created": 1700000000,
                  "model": "test-model",
                  "choices": [{
                    "index": 0,
                    "message": {
                      "role": "assistant",
                      "content": null,
                      "tool_calls": [{
                        "id": "call_test_001",
                        "type": "function",
                        "function": {
                          "name": "%s",
                          "arguments": "%s"
                        }
                      }]
                    },
                    "finish_reason": "tool_calls"
                  }]
                }
                """, toolName, escapeJson(toolArgsJson));
    }

    private String jsonResponse(String content, String finishReason) {
        return String.format("""
                {
                  "id": "chatcmpl-test",
                  "object": "chat.completion",
                  "created": 1700000000,
                  "model": "test-model",
                  "choices": [{
                    "index": 0,
                    "message": {
                      "role": "assistant",
                      "content": "%s"
                    },
                    "finish_reason": "%s"
                  }],
                  "usage": {
                    "prompt_tokens": 10,
                    "completion_tokens": 5,
                    "total_tokens": 15
                  }
                }
                """, escapeJson(content), finishReason);
    }

    private String jsonChunk(String content, boolean isLast) {
        String finishReason = isLast ? "\"finish_reason\":\"stop\"" : "\"finish_reason\":null";
        return String.format("""
                {"id":"chatcmpl-test","object":"chat.completion.chunk","choices":[{"index":0,"delta":{"content":"%s"},%s}]}
                """, escapeJson(content), finishReason);
    }

    private String chatStreamBody(String[] chunks) {
        StringBuilder sse = new StringBuilder();
        for (int i = 0; i < chunks.length; i++) {
            sse.append("data: ").append(jsonChunk(chunks[i], i == chunks.length - 1)).append("\n\n");
        }
        sse.append("data: [DONE]\n\n");
        return sse.toString();
    }

    private String toolCallStreamBody(String toolName, String toolArgsJson) {
        return String.format("""
                data: {"id":"chatcmpl-test","object":"chat.completion.chunk","choices":[{"index":0,"delta":{"role":"assistant","content":null,"tool_calls":[{"index":0,"id":"call_test_001","type":"function","function":{"name":"%s","arguments":""}}]}}]}
                
                data: {"id":"chatcmpl-test","object":"chat.completion.chunk","choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"function":{"arguments":"%s"}}]}}]}
                
                data: {"id":"chatcmpl-test","object":"chat.completion.chunk","choices":[{"index":0,"delta":{},"finish_reason":"tool_calls"}]}
                
                data: [DONE]
                
                """, toolName, escapeJson(toolArgsJson));
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}