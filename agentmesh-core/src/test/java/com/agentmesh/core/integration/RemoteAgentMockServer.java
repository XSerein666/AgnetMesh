package com.agentmesh.core.integration;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

/**
 * WireMock 模拟远程 A2A Agent。
 * 提供 AgentCard 发现、Chat 调用、Task 提交/轮询 等端点。
 */
public class RemoteAgentMockServer {

    private final WireMockServer server;
    private final String agentId;

    public RemoteAgentMockServer(String agentId) {
        this.agentId = agentId;
        this.server = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
    }

    public void start() {
        server.start();
        WireMock.configureFor(server.port());
    }

    public void stop() {
        server.stop();
    }

    public int port() {
        return server.port();
    }

    public String baseUrl() {
        return "http://localhost:" + server.port();
    }

    // ========== AgentCard 发现 ==========

    public void stubAgentCard(String name, String description, String skillJson) {
        String body = String.format("""
                {
                  "agentId": "%s",
                  "name": "%s",
                  "description": "%s",
                  "version": "1.0.0",
                  "url": "%s",
                  "skills": %s
                }
                """, agentId, name, description, baseUrl(), skillJson);

        stubFor(get(urlPathEqualTo("/.well-known/agent.json"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));
    }

    // ========== Chat 端点 ==========

    public void stubChatResponse(String taskId, String output) {
        stubFor(post(urlPathEqualTo("/chat"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(String.format("""
                                {"taskId":"%s"}
                                """, taskId))));

        // 轮询 Task 结果
        stubFor(get(urlPathEqualTo("/task/" + taskId))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(String.format("""
                                {
                                  "taskId": "%s",
                                  "status": "COMPLETED",
                                  "output": {"result": "%s"}
                                }
                                """, taskId, output))));
    }

    // ========== Task 提交 ==========

    public void stubTaskSubmit(String taskId) {
        stubFor(post(urlPathEqualTo("/task"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(String.format("""
                                {"taskId":"%s","status":"PENDING"}
                                """, taskId))));
    }

    public void stubTaskPolling(String taskId, String output, int delayCycles) {
        // 前 N 次返回 PENDING，之后返回 COMPLETED
        for (int i = 0; i < delayCycles; i++) {
            stubFor(get(urlPathEqualTo("/task/" + taskId))
                    .inScenario("task-polling")
                    .whenScenarioStateIs(i == 0 ? "Started" : "pending-" + i)
                    .willReturn(aResponse()
                            .withHeader("Content-Type", "application/json")
                            .withBody(String.format("""
                                    {"taskId":"%s","status":"PENDING"}
                                    """, taskId)))
                    .willSetStateTo("pending-" + (i + 1)));
        }

        stubFor(get(urlPathEqualTo("/task/" + taskId))
                .inScenario("task-polling")
                .whenScenarioStateIs("pending-" + delayCycles)
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(String.format("""
                                {
                                  "taskId": "%s",
                                  "status": "COMPLETED",
                                  "output": {"result": "%s"}
                                }
                                """, taskId, output))));
    }

    // ========== 错误场景 ==========

    public void stubServerError() {
        stubFor(get(urlPathEqualTo("/.well-known/agent.json"))
                .willReturn(aResponse().withStatus(500)));
    }

    public void stubTimeout() {
        stubFor(get(urlPathEqualTo("/.well-known/agent.json"))
                .willReturn(aResponse().withFixedDelay(30_000)));
    }

    public void resetAll() {
        server.resetAll();
    }
}