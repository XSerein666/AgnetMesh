package com.agentmesh.core.agent;

import com.agentmesh.core.prompt.PromptTemplateEngine;

import java.util.*;

/**
 * AgentMesh Actuator 端点
 * 提供 Prompt 模板预览和管理功能
 * 使用时需在配置类中注册为 @Bean
 */
public class AgentMeshActuator {

    private final PromptTemplateEngine engine;

    public AgentMeshActuator(PromptTemplateEngine engine) {
        this.engine = engine;
    }

    public Map<String, Object> listPrompts() {
        Map<String, Object> result = new LinkedHashMap<>();

        List<Map<String, String>> templates = new ArrayList<>();
        for (String name : engine.getRegisteredTemplateNames()) {
            Map<String, String> info = new LinkedHashMap<>();
            info.put("name", name);
            info.put("source", engine.isUserOverride(name) ? "user-override" : "default");
            info.put("path", engine.getTemplatePath(name));
            templates.add(info);
        }

        result.put("templates", templates);
        result.put("activeCount", templates.size());
        return result;
    }

    public Map<String, Object> previewPrompt(String name) {
        String rendered = engine.render(name, null);
        return Map.of(
            "name", name,
            "source", engine.isUserOverride(name) ? "user-override" : "default",
            "rendered", rendered,
            "estimatedTokens", engine.estimateTokens(rendered)
        );
    }
}