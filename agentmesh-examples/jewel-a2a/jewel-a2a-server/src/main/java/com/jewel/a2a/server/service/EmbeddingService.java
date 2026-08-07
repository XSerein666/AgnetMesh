package com.jewel.a2a.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * 文本向量化服务（调用 DashScope text-embedding-v4）
 */
@Slf4j
@Service
public class EmbeddingService {

    private static final String API_URL =
            "https://dashscope.aliyuncs.com/api/v1/services/embeddings/text-embedding/text-embedding";
    private static final String MODEL = "text-embedding-v4";

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${dashscope.api-key}")
    private String apiKey;

    @SuppressWarnings("unchecked")
    public List<Double> embed(String text) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + apiKey);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", MODEL);
            Map<String, Object> input = new LinkedHashMap<>();
            input.put("texts", List.of(text));
            body.put("input", input);

            HttpEntity<String> request = new HttpEntity<>(objectMapper.writeValueAsString(body), headers);
            ResponseEntity<String> response = restTemplate.postForEntity(API_URL, request, String.class);

            Map<String, Object> respBody = objectMapper.readValue(response.getBody(), Map.class);
            Map<String, Object> output = (Map<String, Object>) respBody.get("output");
            List<Map<String, Object>> embeddings = (List<Map<String, Object>>) output.get("embeddings");
            return (List<Double>) embeddings.get(0).get("embedding");
        } catch (Exception e) {
            log.error("[EmbeddingService] 向量化失败", e);
            throw new RuntimeException("向量化失败: " + e.getMessage(), e);
        }
    }
}
