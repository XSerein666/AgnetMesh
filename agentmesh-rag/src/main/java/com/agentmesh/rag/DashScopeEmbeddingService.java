package com.agentmesh.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DashScope 向量化服务实现
 * 模型和 API Key 由 RagConfig 注入，不硬编码
 */
@Slf4j
public class DashScopeEmbeddingService implements EmbeddingService {

    private static final String API_URL =
            "https://dashscope.aliyuncs.com/api/v1/services/embeddings/text-embedding/text-embedding";

    private final String apiKey;
    private final String model;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DashScopeEmbeddingService(RagConfig config) {
        this.apiKey = config.getEmbeddingApiKey();
        this.model = config.getEmbeddingModel();
        this.restTemplate = createRestTemplate();
    }

    private RestTemplate createRestTemplate() {
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(30));
        return new RestTemplate(factory);
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Double> embed(String text) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + apiKey);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model);
            Map<String, Object> input = new LinkedHashMap<>();
            input.put("texts", List.of(text));
            body.put("input", input);

            HttpEntity<String> request = new HttpEntity<>(
                    objectMapper.writeValueAsString(body), headers);
            ResponseEntity<String> response = restTemplate.postForEntity(
                    API_URL, request, String.class);

            Map<String, Object> respBody = objectMapper.readValue(response.getBody(), Map.class);
            Map<String, Object> output = (Map<String, Object>) respBody.get("output");
            List<Map<String, Object>> embeddings = (List<Map<String, Object>>) output.get("embeddings");
            return (List<Double>) embeddings.get(0).get("embedding");
        } catch (Exception e) {
            log.error("[DashScopeEmbedding] 向量化失败", e);
            throw new RuntimeException("向量化失败: " + e.getMessage(), e);
        }
    }
}