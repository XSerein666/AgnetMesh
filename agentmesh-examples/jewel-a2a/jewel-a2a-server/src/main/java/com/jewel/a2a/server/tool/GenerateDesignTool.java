package com.jewel.a2a.server.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.agentmesh.core.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tool 2：珠宝绘图（调用 DashScope wanx2.1-t2i 真实出图）
 */
@Slf4j
@Component
public class GenerateDesignTool implements Tool<Map<String, Object>, Object> {

    private static final String CREATE_URL = "https://dashscope.aliyuncs.com/api/v1/services/aigc/text2image/image-synthesis";
    private static final String TASK_URL = "https://dashscope.aliyuncs.com/api/v1/tasks/";
    private static final String MODEL = "wanx2.1-t2i-turbo";
    private static final String SIZE = "1024*1024";
    private static final int MAX_POLL = 30;
    private static final long POLL_INTERVAL_MS = 2000;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${dashscope.api-key}")
    private String apiKey;

    @Override
    public String getId() {
        return "generate_jewelry_design";
    }

    @Override
    public String getDescription() {
        return "根据设计参数生成专业级珠宝设计图";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "prompt", Map.of("type", "string", "description", "自然语言描述")
                ),
                "required", List.of("prompt")
        );
    }

    @Override
    public Object execute(Map<String, Object> input) {
        String userPrompt = (String) input.getOrDefault("prompt", "一枚精美的钻戒");
        String fullPrompt = PromptTemplates.buildPrompt(userPrompt);
        log.info("[GenerateDesignTool] 开始生图, prompt={}", fullPrompt);

        try {
            // 1. 提交生图任务
            String taskId = submitTask(fullPrompt);
            log.info("[GenerateDesignTool] 任务已提交, taskId={}", taskId);

            // 2. 轮询等待结果
            String imageUrl = pollTask(taskId);
            log.info("[GenerateDesignTool] 生图完成, imageUrl={}", imageUrl);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("imageUrl", imageUrl);
            result.put("prompt", userPrompt);
            result.put("fullPrompt", fullPrompt);
            return result;

        } catch (Exception e) {
            log.error("[GenerateDesignTool] 生图失败", e);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("error", e.getMessage());
            result.put("prompt", userPrompt);
            return result;
        }
    }

    @SuppressWarnings("unchecked")
    private String submitTask(String prompt) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + apiKey);
        headers.set("X-DashScope-Async", "enable");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", MODEL);

        Map<String, Object> inputParam = new LinkedHashMap<>();
        inputParam.put("prompt", prompt);
        body.put("input", inputParam);

        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("size", SIZE);
        parameters.put("n", 1);
        body.put("parameters", parameters);

        HttpEntity<String> request = new HttpEntity<>(objectMapper.writeValueAsString(body), headers);
        ResponseEntity<String> response = restTemplate.postForEntity(CREATE_URL, request, String.class);

        Map<String, Object> respBody = objectMapper.readValue(response.getBody(), Map.class);
        Map<String, Object> output = (Map<String, Object>) respBody.get("output");
        String taskStatus = (String) output.get("task_status");

        if ("FAILED".equals(taskStatus)) {
            String message = output.getOrDefault("message", "未知错误").toString();
            throw new RuntimeException("任务提交失败: " + message);
        }

        return (String) output.get("task_id");
    }

    @SuppressWarnings("unchecked")
    private String pollTask(String taskId) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + apiKey);

        for (int i = 0; i < MAX_POLL; i++) {
            HttpEntity<Void> request = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(
                    TASK_URL + taskId, HttpMethod.GET, request, String.class);

            Map<String, Object> respBody = objectMapper.readValue(response.getBody(), Map.class);
            Map<String, Object> output = (Map<String, Object>) respBody.get("output");
            String taskStatus = (String) output.get("task_status");

            log.info("[GenerateDesignTool] 轮询状态: {} (第{}次)", taskStatus, i + 1);

            if ("SUCCEEDED".equals(taskStatus)) {
                List<Map<String, Object>> results = (List<Map<String, Object>>) output.get("results");
                return (String) results.get(0).get("url");
            } else if ("FAILED".equals(taskStatus)) {
                String message = output.getOrDefault("message", "未知错误").toString();
                throw new RuntimeException("生图任务失败: " + message);
            }

            Thread.sleep(POLL_INTERVAL_MS);
        }

        throw new RuntimeException("生图任务超时, 已轮询 " + MAX_POLL + " 次");
    }
}