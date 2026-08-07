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
 * DashScope LLM 客户端实现
 * 支持文本对话、function calling、多模态视觉
 */
@Slf4j
public class DashScopeLlmClient extends AbstractStreamingLlmClient {

    private static final String TEXT_API_URL =
            "https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation";
    private static final String VISION_API_URL =
            "https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation";
    private static final String PROVIDER = "dashscope";

    private final String apiKey;
    private final String textModel;
    private final String visionModel;
    private final RestTemplate restTemplate;

    public DashScopeLlmClient(String apiKey) {
        this(apiKey, null);
    }

    public DashScopeLlmClient(String apiKey, AgentMeshMetrics metrics) {
        this(apiKey, "qwen-plus", "qwen-vl-plus", new com.agentmesh.core.llm.adapter.DashScopeAdapter(), metrics);
    }

    public DashScopeLlmClient(String apiKey, String textModel, String visionModel, ProviderAdapter adapter) {
        this(apiKey, textModel, visionModel, adapter, null);
    }

    public DashScopeLlmClient(String apiKey, String textModel, String visionModel,
                              ProviderAdapter adapter, AgentMeshMetrics metrics) {
        super(adapter, metrics);
        this.apiKey = apiKey;
        this.textModel = textModel;
        this.visionModel = visionModel;
        this.restTemplate = createRestTemplate();
    }

    @Override
    protected String getProvider() {
        return PROVIDER;
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
    @SuppressWarnings("unchecked")
    public String chat(List<Map<String, Object>> messages) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + apiKey);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", textModel);
            Map<String, Object> input = new LinkedHashMap<>();
            input.put("messages", messages);
            body.put("input", input);

            HttpEntity<String> request = new HttpEntity<>(objectMapper.writeValueAsString(body), headers);
            ResponseEntity<String> response = restTemplate.postForEntity(TEXT_API_URL, request, String.class);

            String responseBody = response.getBody();
            log.debug("[DashScopeLlmClient] 文本响应: {}", responseBody);

            Map<String, Object> respBody = objectMapper.readValue(responseBody, Map.class);
            recordLlmMetrics(respBody);
            Map<String, Object> output = (Map<String, Object>) respBody.get("output");
            if (output == null) {
                throw new RuntimeException("API 响应缺少 output 字段: " + responseBody);
            }
            String text = (String) output.get("text");
            if (text != null) {
                return text;
            }
            List<Map<String, Object>> choices = (List<Map<String, Object>>) output.get("choices");
            if (choices != null && !choices.isEmpty()) {
                Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                return (String) message.get("content");
            }
            throw new RuntimeException("无法解析 API 响应: " + responseBody);
        } catch (Exception e) {
            log.error("[DashScopeLlmClient] 文本调用失败", e);
            throw new RuntimeException("LLM 调用失败: " + e.getMessage(), e);
        }
    }

    @Override
    public LlmChatResponse chatWithTools(List<Map<String, Object>> messages,
                                          List<ToolDefinition> tools,
                                          ToolChoice toolChoice) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + apiKey);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", textModel);
            Map<String, Object> input = new LinkedHashMap<>();
            input.put("messages", messages);
            body.put("input", input);

            // 添加工具定义（DashScope 要求放在 parameters 内）
            Map<String, Object> parameters = new LinkedHashMap<>();
            parameters.put("result_format", "message"); // 必须设为 message 才能返回 tool_calls
            parameters.put("tools", adapter.adaptTools(tools));
            parameters.put("tool_choice", adapter.adaptToolChoice(toolChoice));
            body.put("parameters", parameters);

            String requestBody = objectMapper.writeValueAsString(body);
            log.debug("[DashScopeLlmClient] 工具调用请求: {}", requestBody);
            HttpEntity<String> request = new HttpEntity<>(requestBody, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(TEXT_API_URL, request, String.class);

            String responseBody = response.getBody();
            log.debug("[DashScopeLlmClient] 工具调用响应: {}", responseBody);

            Map<String, Object> respBody = objectMapper.readValue(responseBody, Map.class);
            recordLlmMetrics(respBody);
            return adapter.adaptResponse(responseBody);
        } catch (Exception e) {
            log.error("[DashScopeLlmClient] 工具调用失败", e);
            throw new RuntimeException("LLM 工具调用失败: " + e.getMessage(), e);
        }
    }

    @Override
    public Flux<StreamEvent> chatWithToolsStream(List<Map<String, Object>> messages,
                                                  List<ToolDefinition> tools,
                                                  ToolChoice toolChoice) {
        Map<String, Object> body = buildRequestBody(messages, tools, toolChoice);
        Map<String, Object> parameters = (Map<String, Object>) body.get("parameters");
        parameters.put("stream", true);
        parameters.put("incremental_output", true);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + apiKey);
        headers.set("X-DashScope-SSE", "enable");

        // DashScope 使用 SSE 格式
        return doStream(body, TEXT_API_URL, headers, true, restTemplate);
    }

    @SuppressWarnings("unchecked")
    private void recordLlmMetrics(Map<String, Object> respBody) {
        if (metrics == null) {
            return;
        }
        metrics.recordLlmCall(PROVIDER);
        Map<String, Object> usage = (Map<String, Object>) respBody.get("usage");
        if (usage != null) {
            Object inputTokens = usage.get("input_tokens");
            Object outputTokens = usage.get("output_tokens");
            if (inputTokens instanceof Number) {
                metrics.recordLlmTokens(PROVIDER, "input", ((Number) inputTokens).longValue());
            }
            if (outputTokens instanceof Number) {
                metrics.recordLlmTokens(PROVIDER, "output", ((Number) outputTokens).longValue());
            }
        }
    }

    private Map<String, Object> buildRequestBody(List<Map<String, Object>> messages,
                                                  List<ToolDefinition> tools,
                                                  ToolChoice toolChoice) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", textModel);
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("messages", messages);
        body.put("input", input);

        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("result_format", "message");
        if (tools != null && !tools.isEmpty()) {
            parameters.put("tools", adapter.adaptTools(tools));
            parameters.put("tool_choice", adapter.adaptToolChoice(toolChoice));
        }
        body.put("parameters", parameters);
        return body;
    }

    @Override
    @SuppressWarnings("unchecked")
    public String vision(String imageUrl, String prompt) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + apiKey);

            Map<String, Object> userMsg = new LinkedHashMap<>();
            userMsg.put("role", "user");
            userMsg.put("content", List.of(
                    Map.of("image", imageUrl),
                    Map.of("text", prompt)
            ));

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", visionModel);
            Map<String, Object> input = new LinkedHashMap<>();
            input.put("messages", List.of(userMsg));
            body.put("input", input);

            HttpEntity<String> request = new HttpEntity<>(objectMapper.writeValueAsString(body), headers);
            ResponseEntity<String> response = restTemplate.postForEntity(VISION_API_URL, request, String.class);

            String responseBody = response.getBody();
            log.debug("[DashScopeLlmClient] 视觉响应: {}", responseBody);

            Map<String, Object> respBody = objectMapper.readValue(responseBody, Map.class);
            Map<String, Object> output = (Map<String, Object>) respBody.get("output");
            List<Map<String, Object>> choices = (List<Map<String, Object>>) output.get("choices");
            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
            List<Map<String, Object>> content = (List<Map<String, Object>>) message.get("content");
            return (String) content.get(0).get("text");
        } catch (Exception e) {
            log.error("[DashScopeLlmClient] 视觉调用失败", e);
            throw new RuntimeException("Vision 调用失败: " + e.getMessage(), e);
        }
    }
}
