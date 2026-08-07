package com.jewel.a2a.server.tool;

/**
 * 珠宝专业 Prompt 模板
 */
public class PromptTemplates {

    private static final String JEWELRY_PREFIX = """
            专业珠宝摄影，高清渲染，金属质感，光影折射，
            白色背景，商业摄影级别，细节清晰，微距拍摄效果。
            珠宝设计图：""";

    /**
     * 拼接专业前缀，生成完整绘图 Prompt
     */
    public static String buildPrompt(String userPrompt) {
        return JEWELRY_PREFIX + userPrompt;
    }
}