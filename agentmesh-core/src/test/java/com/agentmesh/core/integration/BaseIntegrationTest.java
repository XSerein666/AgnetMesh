package com.agentmesh.core.integration;

import com.agentmesh.core.tool.Tool;
import com.agentmesh.core.tool.ToolRegistry;
import com.agentmesh.core.infrastructure.AgentMeshMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.util.List;
import java.util.Map;

/**
 * 集成测试基类：提供 WireMock LLM 服务器和工具注册表。
 */
public abstract class BaseIntegrationTest {

    protected LlmMockServer llmMock;
    protected ToolRegistry toolRegistry;

    @BeforeEach
    void setUpBase() {
        llmMock = new LlmMockServer();
        llmMock.start();

        // 注册测试工具
        Tool<?, ?> weatherTool = new Tool<Map<String, Object>, Map<String, Object>>() {
            @Override public String getId() { return "get_weather"; }
            @Override public String getDescription() { return "查询天气"; }
            @Override public Map<String, Object> getInputSchema() {
                return Map.of("type", "object", "properties",
                        Map.of("city", Map.of("type", "string")));
            }
            @Override public Map<String, Object> execute(Map<String, Object> input) {
                return Map.of("temperature", "25°C", "city", input.get("city"));
            }
        };

        Tool<?, ?> searchTool = new Tool<Map<String, Object>, Map<String, Object>>() {
            @Override public String getId() { return "search_knowledge"; }
            @Override public String getDescription() { return "搜索知识库"; }
            @Override public Map<String, Object> getInputSchema() {
                return Map.of("type", "object", "properties",
                        Map.of("query", Map.of("type", "string")));
            }
            @Override public Map<String, Object> execute(Map<String, Object> input) {
                return Map.of("results", List.of("知识1", "知识2"));
            }
        };

        toolRegistry = new ToolRegistry(
                List.of(weatherTool, searchTool),
                new AgentMeshMetrics(new SimpleMeterRegistry()),
                30);
    }

    @AfterEach
    void tearDownBase() {
        if (llmMock != null) {
            llmMock.stop();
        }
    }
}