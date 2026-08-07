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
 * Ollama LLM 客户端实现
 * 支持 /api/chat 端点，部分模型支持原生 function calling
 */
@Slf4j
public class OllamaLlmClient extends AbstractStreamingLlmClient {

    private final String baseUrl;
    private final String model;
    private final RestTemplate restTemplate;

    public OllamaLlmClient(String baseUrl, String model, ProviderAdapter adapter) {
        this(baseUrl, model, adapter, null);
    }

    public OllamaLlmClient(String baseUrl, String model, ProviderAdapter adapter, AgentMeshMetrics metrics) {
        super(adapter, metrics);
        this.baseUrl = baseUrl;
        this.model = model;
        this.restTemplate = createRestTemplate();
    }

    @Override
    protected String getProvider() {
        return "ollama";
    }

    private RestTemplate createRestTemplate() {
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(120));
        return new RestTemplate(factory);
    }

    @Override
    public boolean supportsFunctionCalling() {
        // Ollama 部分模型支持原生 function calling，默认返回 true
        // 可在子类中覆盖此方法
        return true;
    }

    @Override
    public String chat(List<Map<String, Object>> messages) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model);
            body.put("messages", messages);
            body.put("stream", false);

            String url = baseUrl + "/api/chat";
            String rawResponse = doPost(url, body);
            LlmChatResponse response = adapter.adaptResponse(rawResponse);
            return response.getContent() != null ? response.getContent() : "";
        } catch (Exception e) {
            log.error("[OllamaLlmClient] 文本调用失败", e);
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
            body.put("stream", false);

            String url = baseUrl + "/api/chat";
            String rawResponse = doPost(url, body);
            return adapter.adaptResponse(rawResponse);
        } catch (Exception e) {
            log.error("[OllamaLlmClient] 工具调用失败", e);
            throw new RuntimeException("LLM 工具调用失败: " + e.getMessage(), e);
        }
    }

    /**
     * 流式调用：Ollama 使用 NDJSON 格式（每行完整 JSON，无 data: 前缀，无 [DONE]）
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
        }
        body.put("stream", true);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Accept", "application/x-ndjson");

        String url = baseUrl + "/api/chat";
        // sseFormat=false 表示 NDJSON 格式
        return doStream(body, url, headers, false, restTemplate);
    }

    private String doPost(String url, Map<String, Object> body) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> request = new HttpEntity<>(objectMapper.writeValueAsString(body), headers);
        ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
        return response.getBody();
    }
}
