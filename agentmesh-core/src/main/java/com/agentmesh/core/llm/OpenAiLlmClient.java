package com.agentmesh.core.llm;

import com.agentmesh.core.infrastructure.AgentMeshMetrics;
import com.agentmesh.core.llm.adapter.ProviderAdapter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI LLM 客户端实现
 * 支持 chat/completions 端点，原生 function calling
 */
@Slf4j
public class OpenAiLlmClient extends AbstractStreamingLlmClient {

    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final RestTemplate restTemplate;

    public OpenAiLlmClient(String apiKey, String baseUrl, String model, ProviderAdapter adapter) {
        this(apiKey, baseUrl, model, adapter, null);
    }

    public OpenAiLlmClient(String apiKey, String baseUrl, String model,
                            ProviderAdapter adapter, AgentMeshMetrics metrics) {
        super(adapter, metrics);
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.model = model;
        this.restTemplate = createRestTemplate();
    }

    @Override
    protected String getProvider() {
        return "openai";
    }

    private RestTemplate createRestTemplate() {
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(60));
        return new RestTemplate(factory);
    }

    @Override
    public boolean supportsFunctionCalling() {
        return true;
    }

    @Override
    public String chat(List<Map<String, Object>> messages) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model);
            body.put("messages", messages);

            String url = baseUrl + "/chat/completions";
            String rawResponse = doPost(url, body);
            LlmChatResponse response = adapter.adaptResponse(rawResponse);
            return response.getContent() != null ? response.getContent() : "";
        } catch (Exception e) {
            log.error("[OpenAiLlmClient] 文本调用失败", e);
            throw new RuntimeException("LLM 调用失败: " + e.getMessage(), e);
        }
    }

    @Override
    public LlmChatResponse chatWithTools(List<Map<String, Object>> messages,
                                          List<ToolDefinition> tools,
                                          ToolChoice toolChoice) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model);
            body.put("messages", messages);
            body.put("tools", adapter.adaptTools(tools));
            body.put("tool_choice", adapter.adaptToolChoice(toolChoice));

            String url = baseUrl + "/chat/completions";
            String rawResponse = doPost(url, body);
            return adapter.adaptResponse(rawResponse);
        } catch (Exception e) {
            log.error("[OpenAiLlmClient] 工具调用失败", e);
            throw new RuntimeException("LLM 工具调用失败: " + e.getMessage(), e);
        }
    }

    /**
     * 流式调用：OpenAI 使用 SSE 格式（data: 前缀 + [DONE] 结束）
     */
    @Override
    public Flux<StreamEvent> chatWithToolsStream(List<Map<String, Object>> messages,
                                                  List<ToolDefinition> tools,
                                                  ToolChoice toolChoice) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        if (tools != null && !tools.isEmpty()) {
            body.put("tools", adapter.adaptTools(tools));
            body.put("tool_choice", adapter.adaptToolChoice(toolChoice));
        }
        body.put("stream", true);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + apiKey);
        headers.set("Accept", "text/event-stream");

        String url = baseUrl + "/chat/completions";
        return doStream(body, url, headers, true, restTemplate);
    }

    private String doPost(String url, Map<String, Object> body) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + apiKey);

        HttpEntity<String> request = new HttpEntity<>(objectMapper.writeValueAsString(body), headers);
        ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
        return response.getBody();
    }
}
