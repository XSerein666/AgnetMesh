package com.agentmesh.core.prompt;

import com.agentmesh.core.tool.ToolRegistry;

import java.util.ArrayList;
import java.util.List;

/**
 * Prompt 编程式构建器
 * 作为文件模板的补充，支持代码中动态构建 Prompt
 */
public class PromptBuilder {

    private final List<String> sections = new ArrayList<>();
    private final ToolRegistry toolRegistry;
    private final TokenBudgetManager tokenBudgetManager;

    public PromptBuilder(ToolRegistry toolRegistry, TokenBudgetManager tokenBudgetManager) {
        this.toolRegistry = toolRegistry;
        this.tokenBudgetManager = tokenBudgetManager;
    }

    public static PromptBuilder create(ToolRegistry toolRegistry, TokenBudgetManager tokenBudgetManager) {
        return new PromptBuilder(toolRegistry, tokenBudgetManager);
    }

    /** 添加系统角色定义 */
    public PromptBuilder system(String text) {
        sections.add(text);
        return this;
    }

    /** 自动注入工具列表 */
    public PromptBuilder withTools() {
        StringBuilder sb = new StringBuilder();
        sb.append("你可以使用以下工具：\n");
        for (String id : toolRegistry.getAllToolIds()) {
            var tool = toolRegistry.getTool(id);
            sb.append("- ").append(id).append(": ").append(tool.getDescription()).append("\n");
            sb.append("  参数: ").append(tool.getInputSchema()).append("\n");
        }
        sections.add(sb.toString());
        return this;
    }

    /** 注入自定义上下文（受 Token 预算控制） */
    public PromptBuilder withContext(String context) {
        if (context != null && !context.isEmpty()) {
            String truncated = tokenBudgetManager.truncate(context);
            sections.add("\n\n【参考知识】\n" + truncated);
        }
        return this;
    }

    /** 注入自定义指令 */
    public PromptBuilder instructions(String text) {
        sections.add(text);
        return this;
    }

    /** 构建最终 Prompt */
    public String build() {
        return String.join("\n\n", sections);
    }
}
