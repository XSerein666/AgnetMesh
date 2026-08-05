package com.agentmesh.core.prompt;

import com.agentmesh.core.tool.ToolRegistry;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Prompt 模板引擎
 * 支持从文件加载模板并进行变量插值
 */
@Slf4j
public class PromptTemplateEngine {

    private static final Pattern VAR_PATTERN = Pattern.compile("\\$\\{(\\w+)}");

    private final ToolRegistry toolRegistry;
    private final TokenBudgetManager tokenBudgetManager;
    private final Map<String, String> templateCache = new ConcurrentHashMap<>();
    private final Map<String, TemplateSource> templateSources = new ConcurrentHashMap<>();

    private enum Source { BUILTIN, USER_OVERRIDE }

    private record TemplateSource(String path, Source source) {}

    public PromptTemplateEngine(ToolRegistry toolRegistry, TokenBudgetManager tokenBudgetManager) {
        this.toolRegistry = toolRegistry;
        this.tokenBudgetManager = tokenBudgetManager;
    }

    /**
     * 从文件加载并渲染模板
     */
    public String render(String templateName, Map<String, String> extraVars) {
        String template = loadTemplate(templateName);
        // 拷贝一份，避免修改调用方的 Map（副作用）
        Map<String, String> vars = extraVars != null ? new HashMap<>(extraVars) : new HashMap<>();
        // 对 context 变量做 Token 预算控制
        if (vars.containsKey("context")) {
            String rawContext = vars.get("context");
            String truncated = tokenBudgetManager.truncate(rawContext);
            vars.put("context", truncated);
        }
        // 对 context_summary 变量也做 Token 预算控制
        if (vars.containsKey("context_summary")) {
            String rawSummary = vars.get("context_summary");
            String truncated = tokenBudgetManager.truncate(rawSummary);
            vars.put("context_summary", truncated);
        }
        return renderString(template, vars);
    }

    /**
     * 渲染模板字符串
     */
    public String renderString(String template, Map<String, String> extraVars) {
        Map<String, String> vars = buildDefaultVars();
        if (extraVars != null) {
            vars.putAll(extraVars);
        }

        Matcher matcher = VAR_PATTERN.matcher(template);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String varName = matcher.group(1);
            String replacement = vars.getOrDefault(varName, matcher.group(0));
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private Map<String, String> buildDefaultVars() {
        Map<String, String> vars = new HashMap<>();
        vars.put("tool_list", buildToolList());
        vars.put("tool_list_json", buildToolListJson());
        vars.put("current_date", LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE));
        return vars;
    }

    /**
     * 估算渲染后 Prompt 的 Token 数量（供 Actuator 端点使用）
     */
    public int estimateTokens(String rendered) {
        return tokenBudgetManager.estimateTokens(rendered);
    }

    private String buildToolList() {
        StringBuilder sb = new StringBuilder();
        for (String id : toolRegistry.getAllToolIds()) {
            var tool = toolRegistry.getTool(id);
            sb.append("- ").append(id).append(": ").append(tool.getDescription()).append("\n");
            sb.append("  参数: ").append(tool.getInputSchema()).append("\n");
        }
        return sb.toString();
    }

    private String buildToolListJson() {
        return toolRegistry.toDefinitionsJson();
    }

    private String loadTemplate(String name) {
        return templateCache.computeIfAbsent(name, n -> {
            // 1. 优先查找用户覆盖目录
            String userPath = "config/prompts/" + n + ".prompt";
            java.io.File userFile = new java.io.File(userPath);
            if (userFile.exists()) {
                templateSources.put(n, new TemplateSource(userPath, Source.USER_OVERRIDE));
                try {
                    return new String(java.nio.file.Files.readAllBytes(userFile.toPath()), StandardCharsets.UTF_8);
                } catch (IOException e) {
                    log.error("[PromptEngine] 加载用户模板失败: {}", userPath, e);
                }
            }
            // 2. 回退到 classpath 内置模板
            String path = "prompts/" + n + ".prompt";
            templateSources.put(n, new TemplateSource("classpath:" + path, Source.BUILTIN));
            try (InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {
                if (is == null) {
                    log.error("[PromptEngine] 模板文件不存在: {}", path);
                    throw new IllegalStateException("模板文件不存在: " + path);
                }
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                log.error("[PromptEngine] 加载模板失败: {}", path, e);
                throw new RuntimeException("加载模板失败: " + path, e);
            }
        });
    }

    public boolean isUserOverride(String name) {
        TemplateSource src = templateSources.get(name);
        return src != null && src.source() == Source.USER_OVERRIDE;
    }

    public String getTemplatePath(String name) {
        TemplateSource src = templateSources.get(name);
        return src != null ? src.path() : "unknown";
    }

    public Set<String> getRegisteredTemplateNames() {
        return Collections.unmodifiableSet(templateSources.keySet());
    }

    /** 清除模板缓存，强制重新加载（用于开发调试） */
    public void clearCache() {
        templateCache.clear();
        log.info("[PromptEngine] 模板缓存已清除");
    }

    /** 清除单个模板缓存 */
    public void clearCache(String templateName) {
        templateCache.remove(templateName);
        log.info("[PromptEngine] 模板 '{}' 缓存已清除", templateName);
    }
}