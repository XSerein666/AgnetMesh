package com.agentmesh.core.remote;

import com.agentmesh.core.infrastructure.AgentMeshMetrics;
import com.agentmesh.core.llm.StreamEvent;
import com.agentmesh.core.registry.AgentAuthProperties;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

/**
 * HTTP 实现：通过 A2A 协议调用远程 Agent。
 *
 * 同步流程（Phase 6，RemoteTool 使用）：
 * 1. POST {agentUrl}/a2a/run 提交任务，获取 taskId
 * 2. 轮询 GET {agentUrl}/a2a/task/{taskId} 直到 SUCCESS 或 FAILED
 * 3. 返回 output 或 error
 *
 * 异步流程（Phase 7，Orchestrator 使用）：
 * 使用 WebClient 非阻塞轮询，不占用 Tomcat 线程。
 */
@Slf4j
public class HttpAgentClient implements AgentClient {

    private final RestTemplate restTemplate;
    private final WebClient webClient;
    private final RemoteToolProperties properties;
    private final AgentAuthProperties authProperties;
    private final String selfAgentId;
    private final AgentMeshMetrics metrics;

    public HttpAgentClient(RestTemplate restTemplate, WebClient webClient,
                           RemoteToolProperties properties,
                           AgentAuthProperties authProperties,
                           String selfAgentId) {
        this(restTemplate, webClient, properties, authProperties, selfAgentId, null);
    }

    public HttpAgentClient(RestTemplate restTemplate, WebClient webClient,
                           RemoteToolProperties properties,
                           AgentAuthProperties authProperties,
                           String selfAgentId, AgentMeshMetrics metrics) {
        this.restTemplate = restTemplate;
        this.webClient = webClient;
        this.properties = properties;
        this.authProperties = authProperties;
        this.selfAgentId = selfAgentId;
        this.metrics = metrics;
    }

    // ========== 同步调用（Phase 6，保留向后兼容） ==========

    @Override
    @SuppressWarnings("unchecked")
    public Object callSkill(String agentUrl, String skillId, Map<String, Object> input) {
        long deadline = System.currentTimeMillis() + properties.getTimeout().toMillis();
        String base = agentUrl.endsWith("/") ? agentUrl.substring(0, agentUrl.length() - 1) : agentUrl;

        try {
            Map<String, Object> request = Map.of("skillId", skillId, "input", input);
            Map<String, Object> submitResp = restTemplate.postForObject(
                    base + "/a2a/run", request, Map.class);
            if (submitResp == null || !submitResp.containsKey("taskId")) {
                return Map.of("error", "远程 Agent 返回异常: " + agentUrl);
            }
            String taskId = String.valueOf(submitResp.get("taskId"));
            log.info("[AgentClient] 任务已提交: agentUrl={}, skillId={}, taskId={}", agentUrl, skillId, taskId);

            long pollInterval = properties.getPollInterval().toMillis();
            while (System.currentTimeMillis() < deadline) {
                if (Thread.interrupted()) {
                    log.warn("[AgentClient] 轮询被中断: taskId={}", taskId);
                    return Map.of("error", "远程调用被中断: " + agentUrl + "/" + skillId);
                }

                Thread.sleep(pollInterval);

                Map<String, Object> taskResp = restTemplate.getForObject(
                        base + "/a2a/task/" + taskId, Map.class);
                if (taskResp == null) continue;

                String status = String.valueOf(taskResp.getOrDefault("status", ""));
                if ("SUCCESS".equals(status)) {
                    log.info("[AgentClient] 任务完成: taskId={}", taskId);
                    return taskResp.getOrDefault("output", Map.of());
                }
                if ("FAILED".equals(status)) {
                    String errorMsg = String.valueOf(taskResp.getOrDefault("message", "未知错误"));
                    log.warn("[AgentClient] 任务失败: taskId={}, error={}", taskId, errorMsg);
                    return Map.of("error", "远程 Agent 执行失败: " + errorMsg);
                }
            }

            return Map.of("error", "远程调用超时: " + agentUrl + "/" + skillId);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Map.of("error", "远程调用被中断: " + agentUrl + "/" + skillId);
        } catch (Exception e) {
            log.error("[AgentClient] 远程调用异常: agentUrl={}, skillId={}", agentUrl, skillId, e);
            return Map.of("error", "远程调用失败: " + e.getMessage());
        }
    }

    // ========== 异步调用（Phase 7 新增） ==========

    @Override
    @SuppressWarnings("unchecked")
    public Mono<Object> callSkillAsync(String agentUrl, String skillId, Map<String, Object> input, String agentId) {
        String base = agentUrl.endsWith("/") ? agentUrl.substring(0, agentUrl.length() - 1) : agentUrl;
        Duration timeout = properties.getTimeout();
        Duration pollInterval = properties.getPollInterval();
        long maxAttempts = timeout.toMillis() / pollInterval.toMillis();
        String metricAgentId = agentId != null ? agentId : skillId;

        Timer.Sample sample = metrics != null ? metrics.startRemoteTimer() : null;

        Map<String, Object> request = Map.of("skillId", skillId, "input", input);

        return webClient.post()
                .uri(base + "/a2a/run")
                .headers(headers -> addAuthHeaders(headers))
                .bodyValue(request)
                .retrieve()
                .bodyToMono(Map.class)
                .flatMap(submitResp -> {
                    if (submitResp == null || !submitResp.containsKey("taskId")) {
                        if (metrics != null) {
                            metrics.recordRemoteCall(metricAgentId, "FAILED");
                            metrics.stopRemoteTimer(sample, metricAgentId);
                        }
                        return Mono.just(Map.of("error", "远程 Agent 返回异常: " + agentUrl));
                    }
                    String taskId = String.valueOf(submitResp.get("taskId"));
                    log.info("[AgentClient] 异步任务已提交: agentId={}, skillId={}, taskId={}",
                            metricAgentId, skillId, taskId);

                    return Mono.delay(pollInterval)
                            .repeat()
                            .take(maxAttempts)
                            .flatMapSequential(i -> webClient.get()
                                    .uri(base + "/a2a/task/" + taskId)
                                    .retrieve()
                                    .bodyToMono(Map.class)
                                    .onErrorResume(e -> Mono.just(Map.of("status", "ERROR")))
                            )
                            .filter(taskResp -> {
                                String status = String.valueOf(
                                        taskResp.getOrDefault("status", ""));
                                return "SUCCESS".equals(status) || "FAILED".equals(status);
                            })
                            .next()
                            .flatMap(taskResp -> {
                                String status = String.valueOf(
                                        taskResp.getOrDefault("status", ""));
                                if ("SUCCESS".equals(status)) {
                                    log.info("[AgentClient] 异步任务完成: taskId={}", taskId);
                                    if (metrics != null) {
                                        metrics.recordRemoteCall(metricAgentId, "SUCCESS");
                                        metrics.stopRemoteTimer(sample, metricAgentId);
                                    }
                                    return Mono.just(taskResp.getOrDefault("output", Map.of()));
                                }
                                String errorMsg = String.valueOf(
                                        taskResp.getOrDefault("message", "未知错误"));
                                log.warn("[AgentClient] 异步任务失败: taskId={}, error={}",
                                        taskId, errorMsg);
                                if (metrics != null) {
                                    metrics.recordRemoteCall(metricAgentId, "FAILED");
                                    metrics.stopRemoteTimer(sample, metricAgentId);
                                }
                                return Mono.just(Map.of("error",
                                        "远程 Agent 执行失败: " + errorMsg));
                            })
                            .switchIfEmpty(Mono.defer(() -> {
                                log.warn("[AgentClient] 异步任务超时: agentId={}, taskId={}", metricAgentId, taskId);
                                if (metrics != null) {
                                    metrics.recordRemoteCall(metricAgentId, "TIMEOUT");
                                    metrics.stopRemoteTimer(sample, metricAgentId);
                                }
                                return Mono.just(Map.of("error",
                                        "远程调用超时: " + agentUrl + "/" + skillId));
                            }));
                })
                .onErrorResume(e -> {
                    log.error("[AgentClient] 异步远程调用异常: agentId={}, skillId={}",
                            metricAgentId, skillId, e);
                    if (metrics != null) {
                        metrics.recordRemoteCall(metricAgentId, "FAILED");
                        metrics.stopRemoteTimer(sample, metricAgentId);
                    }
                    return Mono.just(Map.of("error", "远程调用失败: " + e.getMessage()));
                });
    }

    // ========== 流式调用（Phase 8 新增，Phase 9 超时配置化） ==========

    @Override
    public Flux<StreamEvent> callSkillStream(String agentUrl, String message, String agentId) {
        return callSkillStream(agentUrl, message, null, agentId);
    }

    @Override
    public Flux<StreamEvent> callSkillStream(String agentUrl, String message, String traceId, String agentId) {
        String base = agentUrl.endsWith("/") ? agentUrl.substring(0, agentUrl.length() - 1) : agentUrl;
        String metricAgentId = agentId != null ? agentId : "unknown";

        Timer.Sample sample = metrics != null ? metrics.startRemoteTimer() : null;
        log.info("[AgentClient] 流式调用开始: agentId={}, url={}, traceId={}", metricAgentId, agentUrl, traceId);

        Map<String, Object> request = Map.of(
                "message", message,
                "sessionId", "orch-" + java.util.UUID.randomUUID().toString().substring(0, 8)
        );

        return webClient.post()
                .uri(base + "/chat/stream")
                .headers(headers -> {
                    addAuthHeaders(headers);
                    addTraceIdHeader(headers, traceId);
                })
                .bodyValue(request)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .retrieve()
                .bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {})
                .map(ServerSentEvent::data)
                .filter(data -> data != null && !data.isEmpty())
                .map(this::parseStreamEvent)
                .filter(event -> event != null)
                .timeout(properties.getStreamTimeout())
                .doOnComplete(() -> {
                    if (metrics != null) {
                        metrics.recordRemoteCall(metricAgentId, "SUCCESS");
                        metrics.stopRemoteTimer(sample, metricAgentId);
                    }
                    log.info("[AgentClient] 流式调用完成: agentId={}", metricAgentId);
                })
                .onErrorResume(e -> {
                    if (e instanceof java.util.concurrent.TimeoutException) {
                        log.warn("[AgentClient] 流式调用超时: agentId={}", metricAgentId);
                        if (metrics != null) {
                            metrics.recordRemoteCall(metricAgentId, "TIMEOUT");
                            metrics.stopRemoteTimer(sample, metricAgentId);
                        }
                        return Flux.just(StreamEvent.builder()
                                .type(StreamEvent.Type.ERROR)
                                .content("远程调用超时: " + agentUrl)
                                .build());
                    }
                    log.error("[AgentClient] 流式调用异常: agentId={}", metricAgentId, e);
                    if (metrics != null) {
                        metrics.recordRemoteCall(metricAgentId, "FAILED");
                        metrics.stopRemoteTimer(sample, metricAgentId);
                    }
                    return Flux.just(StreamEvent.builder()
                            .type(StreamEvent.Type.ERROR)
                            .content("远程调用失败: " + e.getMessage())
                            .build());
                });
    }

    /**
     * 将 SSE data 字符串反序列化为 StreamEvent。
     */
    private StreamEvent parseStreamEvent(String data) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(data, StreamEvent.class);
        } catch (Exception e) {
            log.warn("[AgentClient] SSE 事件解析失败: {}", data);
            return null;
        }
    }

    /**
     * 添加 Agent 间鉴权请求头。
     * 仅当 authProperties.enabled=true 时添加 X-Agent-Key 和 X-Agent-Id。
     */
    private void addAuthHeaders(org.springframework.http.HttpHeaders headers) {
        if (authProperties.isEnabled() && authProperties.getApiKey() != null
                && !authProperties.getApiKey().isEmpty()) {
            headers.set("X-Agent-Key", authProperties.getApiKey());
            headers.set("X-Agent-Id", selfAgentId);
        }
    }

    /**
     * 添加 traceId 请求头，仅当非空时设置。
     * traceId 为显式参数（不读 ThreadLocal），防止 reactor 线程中拿到 null。
     */
    private void addTraceIdHeader(org.springframework.http.HttpHeaders headers, String traceId) {
        if (traceId != null && !traceId.isEmpty()) {
            headers.set("X-Trace-Id", traceId);
        }
    }
}