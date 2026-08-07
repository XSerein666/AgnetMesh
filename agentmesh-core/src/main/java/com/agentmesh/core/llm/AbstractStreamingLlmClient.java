package com.agentmesh.core.llm;

import com.agentmesh.core.infrastructure.AgentMeshMetrics;
import com.agentmesh.core.llm.adapter.ProviderAdapter;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestTemplate;
import reactor.core.publisher.Flux;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 流式 LLM 客户端骨架，统一 SSE / NDJSON 解析逻辑。
 *
 * 子类职责：
 * - 提供 chat() / chatWithTools() 的非流式实现
 * - 构造请求体、URL、Headers 后委托 doStream() 执行流式
 * - 通过 getProvider() 提供厂商标识（用于 metrics 标签）
 */
@Slf4j
public abstract class AbstractStreamingLlmClient implements LlmClient {

    protected final ObjectMapper objectMapper = new ObjectMapper();
    protected final ProviderAdapter adapter;
    protected final AgentMeshMetrics metrics;

    protected AbstractStreamingLlmClient(ProviderAdapter adapter, AgentMeshMetrics metrics) {
        this.adapter = adapter;
        this.metrics = metrics;
    }

    /**
     * 流式调用模板：发起 HTTP 请求并按行解析事件。
     *
     * @param body      请求体（子类已设置 stream:true 等流式参数）
     * @param url       端点 URL
     * @param headers   请求头（含鉴权 + 流式标记）
     * @param sseFormat true=SSE(data: 前缀，遇 [DONE] 结束)；false=NDJSON(每行完整 JSON)
     */
    protected Flux<StreamEvent> doStream(Map<String, Object> body, String url,
                                          HttpHeaders headers, boolean sseFormat,
                                          RestTemplate restTemplate) {
        return Flux.create(sink -> {
            try {
                if (metrics != null) {
                    metrics.recordLlmCall(getProvider());
                }
                String requestBody = objectMapper.writeValueAsString(body);
                log.debug("[{}] 流式请求: {}", getProvider(), requestBody);

                restTemplate.execute(url, HttpMethod.POST, request -> {
                    headers.forEach((k, values) ->
                            values.forEach(v -> request.getHeaders().add(k, v)));
                    request.getBody().write(requestBody.getBytes(StandardCharsets.UTF_8));
                }, response -> {
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(response.getBody(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            String json = extractJson(line, sseFormat);
                            if (json == null) {
                                continue;
                            }
                            if ("[DONE]".equals(json)) {
                                break;
                            }
                            StreamEvent event = adapter.adaptStreamChunk(json, objectMapper);
                            if (event != null) {
                                sink.next(event);
                            }
                        }
                    }
                    // 统一在流结束时发 DONE，兼容服务端不发 [DONE] 的情况
                    sink.next(StreamEvent.builder().type(StreamEvent.Type.DONE).build());
                    sink.complete();
                    return null;
                });
            } catch (Exception e) {
                log.error("[{}] 流式调用失败", getProvider(), e);
                sink.next(StreamEvent.builder()
                        .type(StreamEvent.Type.ERROR)
                        .content("LLM 流式调用失败: " + e.getMessage())
                        .build());
                sink.complete();
            }
        });
    }

    /**
     * 从原始行提取 JSON 数据。
     * - SSE 格式：以 "data:" 前缀，截取后 trim，非 data: 行返回 null
     * - NDJSON：直接 trim 整行
     */
    private String extractJson(String line, boolean sseFormat) {
        if (sseFormat) {
            if (!line.startsWith("data:")) {
                return null;
            }
            String json = line.substring(5).trim();
            return json.isEmpty() ? null : json;
        }
        String json = line.trim();
        return json.isEmpty() ? null : json;
    }

    /** 厂商标识，用于 metrics 标签 */
    protected abstract String getProvider();
}
