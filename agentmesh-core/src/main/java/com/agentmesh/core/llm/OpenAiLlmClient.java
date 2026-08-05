package com.agentmesh.core.llm;

import com.agentmesh.core.llm.adapter.ProviderAdapter;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI LLM 客户端实现
 * 支持 chat/completions 端点，原生 function calling
 */
@Slf4j
public class OpenAiLlmClient implements LlmClient {

    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final ProviderAdapter adapter;

    public OpenAiLlmClient(String apiKey, String baseUrl, String model, ProviderAdapter adapter) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.model = model;
        this.adapter = adapter;
        this.restTemplate = createRestTemplate();
        this.objectMapper = new ObjectMapper();
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

    private String doPost(String url, Map<String, Object> body) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + apiKey);

        HttpEntity<String> request = new HttpEntity<>(objectMapper.writeValueAsString(body), headers);
        ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
        return response.getBody();
    }
}