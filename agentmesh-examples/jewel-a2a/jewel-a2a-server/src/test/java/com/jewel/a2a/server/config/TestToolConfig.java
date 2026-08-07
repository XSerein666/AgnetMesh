package com.jewel.a2a.server.config;

import com.agentmesh.core.tool.Tool;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.util.List;
import java.util.Map;

/**
 * 测试用 Tool 配置，提供简单的 Tool 实现供 AgentMeshConfig 中的 ToolRegistry 收集。
 */
@TestConfiguration
public class TestToolConfig {

    @Bean
    public Tool<Map<String, Object>, Object> testTool() {
        return new Tool<>() {
            @Override
            public String getId() {
                return "test_tool";
            }

            @Override
            public String getDescription() {
                return "测试工具";
            }

            @Override
            public Map<String, Object> getInputSchema() {
                return Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "input", Map.of("type", "string", "description", "测试输入")
                        ),
                        "required", List.of("input")
                );
            }

            @Override
            public Object execute(Map<String, Object> input) {
                return Map.of("result", "executed: " + input.getOrDefault("input", ""));
            }
        };
    }
}