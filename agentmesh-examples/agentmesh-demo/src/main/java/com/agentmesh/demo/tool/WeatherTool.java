package com.agentmesh.demo.tool;

import com.agentmesh.core.tool.Tool;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 天气查询 Tool（示例）
 */
@Component
public class WeatherTool implements Tool<Map<String, Object>, Map<String, Object>> {

    @Override
    public String getId() {
        return "weather";
    }

    @Override
    public String getDescription() {
        return "查询指定城市的天气信息";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "city", Map.of("type", "string", "description", "城市名称")
                ),
                "required", java.util.List.of("city")
        );
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> input) {
        String city = (String) input.get("city");
        return Map.of(
                "city", city,
                "temperature", "25°C",
                "weather", "晴天",
                "humidity", "60%"
        );
    }
}