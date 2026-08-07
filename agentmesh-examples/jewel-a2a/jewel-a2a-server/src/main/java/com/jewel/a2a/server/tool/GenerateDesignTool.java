package com.jewel.a2a.server.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.agentmesh.core.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Tool 2：珠宝绘图（调用 DashScope wanx2.1-t2i 真实出图）
 * <p>
 * 使用 WebClient + CompletableFuture 异步非阻塞轮询，
 * 替代 RestTemplate + Thread.sleep 同步阻塞方式。
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
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${dashscope.api-key}")
    private String apiKey;

    public GenerateDesignTool() {
        this.webClient = WebClient.builder()
                .codecs(config -> config.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
                .build();
        this.objectMapper = new ObjectMapper();
    }

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
            // 异步提交任务
            String taskId = submitTaskAsync(fullPrompt)
                    .get(REQUEST_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            log.info("[GenerateDesignTool] 任务已提交, taskId={}", taskId);

            // 异步轮询结果
            long pollTimeoutMs = MAX_POLL * POLL_INTERVAL_MS + 5000;
            String imageUrl = pollTaskAsync(taskId)
                    .get(pollTimeoutMs, TimeUnit.MILLISECONDS);
            log.info("[GenerateDesignTool] 生图完成, imageUrl={}", imageUrl);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("imageUrl", imageUrl);
            result.put("prompt", userPrompt);
            result.put("fullPrompt", fullPrompt);
            return result;

        } catch (TimeoutException e) {
            log.error("[GenerateDesignTool] 生图超时", e);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("error", "生图任务超时，请稍后重试");
            result.put("prompt", userPrompt);
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
    private CompletableFuture<String> submitTaskAsync(String prompt) {
        CompletableFuture<String> future = new CompletableFuture<>();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", MODEL);
        Map<String, Object> inputParam = new LinkedHashMap<>();
        inputParam.put("prompt", prompt);
        body.put("input", inputParam);
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("size", SIZE);
        parameters.put("n", 1);
        body.put("parameters", parameters);

        webClient.post()
                .uri(CREATE_URL)
                .header("Authorization", "Bearer " + apiKey)
                .header("X-DashScope-Async", "enable")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(REQUEST_TIMEOUT)
                .subscribe(
                        responseBody -> {
                            try {
                                Map<String, Object> respBody = objectMapper.readValue(responseBody, Map.class);
                                Map<String, Object> output = (Map<String, Object>) respBody.get("output");
                                String taskStatus = (String) output.get("task_status");
                                if ("FAILED".equals(taskStatus)) {
                                    String message = output.getOrDefault("message", "未知错误").toString();
                                    future.completeExceptionally(
                                            new RuntimeException("任务提交失败: " + message));
                                } else {
                                    future.complete((String) output.get("task_id"));
                                }
                            } catch (Exception e) {
                                future.completeExceptionally(e);
                            }
                        },
                        future::completeExceptionally
                );

        return future;
    }

    private CompletableFuture<String> pollTaskAsync(String taskId) {
        CompletableFuture<String> future = new CompletableFuture<>();
        pollRecursive(taskId, 0, future);
        return future;
    }

    @SuppressWarnings("unchecked")
    private void pollRecursive(String taskId, int attempt, CompletableFuture<String> future) {
        if (attempt >= MAX_POLL) {
            future.completeExceptionally(
                    new RuntimeException("生图任务超时, 已轮询 " + MAX_POLL + " 次"));
            return;
        }

        // 非首次轮询先延迟 POLL_INTERVAL_MS，使用 CompletableFuture 异步延迟而非 Thread.sleep
        Runnable doPoll = () -> {
            webClient.get()
                    .uri(TASK_URL + taskId)
                    .header("Authorization", "Bearer " + apiKey)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(REQUEST_TIMEOUT)
                    .subscribe(
                            responseBody -> {
                                try {
                                    Map<String, Object> respBody = objectMapper.readValue(responseBody, Map.class);
                                    Map<String, Object> output = (Map<String, Object>) respBody.get("output");
                                    String taskStatus = (String) output.get("task_status");

                                    log.info("[GenerateDesignTool] 轮询状态: {} (第{}次)",
                                            taskStatus, attempt + 1);

                                    if ("SUCCEEDED".equals(taskStatus)) {
                                        List<Map<String, Object>> results =
                                                (List<Map<String, Object>>) output.get("results");
                                        future.complete((String) results.get(0).get("url"));
                                    } else if ("FAILED".equals(taskStatus)) {
                                        String message = output.getOrDefault("message", "未知错误").toString();
                                        future.completeExceptionally(
                                                new RuntimeException("生图任务失败: " + message));
                                    } else {
                                        pollRecursive(taskId, attempt + 1, future);
                                    }
                                } catch (Exception e) {
                                    future.completeExceptionally(e);
                                }
                            },
                            future::completeExceptionally
                    );
        };

        if (attempt == 0) {
            // 首次轮询立即执行，不延迟
            doPoll.run();
        } else {
            // 后续轮询延迟 POLL_INTERVAL_MS
            CompletableFuture.runAsync(() -> {}, 
                    CompletableFuture.delayedExecutor(POLL_INTERVAL_MS, TimeUnit.MILLISECONDS))
                    .thenRun(doPoll);
        }
    }
}