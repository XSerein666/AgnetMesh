package com.agentmesh.core.prompt;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Token 预算管理器
 * 控制注入 Prompt 的上下文长度，防止超出模型限制
 */
public class TokenBudgetManager {

    /** 上下文最大 Token 数（默认 2000，约为模型上下文的 25%） */
    private final int maxContextTokens;

    public TokenBudgetManager() {
        this(2000);
    }

    public TokenBudgetManager(int maxContextTokens) {
        this.maxContextTokens = maxContextTokens;
    }

    /**
     * 对上下文内容做截断
     * @param context 原始上下文文本
     * @return 截断后的文本
     */
    public String truncate(String context) {
        if (context == null || context.isEmpty()) {
            return "";
        }
        int estimatedTokens = estimateTokens(context);
        if (estimatedTokens <= maxContextTokens) {
            return context;
        }
        // 按段落截断，优先保留前面的内容
        String[] paragraphs = context.split("\n\n");
        StringBuilder result = new StringBuilder();
        int currentTokens = 0;
        for (String para : paragraphs) {
            int paraTokens = estimateTokens(para);
            if (currentTokens + paraTokens > maxContextTokens) {
                break;
            }
            if (!result.isEmpty()) {
                result.append("\n\n");
            }
            result.append(para);
            currentTokens += paraTokens;
        }
        result.append("\n\n[上下文已截断，原始长度约 ").append(estimatedTokens).append(" tokens]");
        return result.toString();
    }

    /**
     * 对检索结果按相关性排序后截断
     */
    public String rerankAndTruncate(List<KnowledgeChunk> chunks) {
        List<KnowledgeChunk> sorted = new ArrayList<>(chunks);
        sorted.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));

        StringBuilder result = new StringBuilder();
        int currentTokens = 0;
        for (KnowledgeChunk chunk : sorted) {
            int chunkTokens = estimateTokens(chunk.getContent());
            if (currentTokens + chunkTokens > maxContextTokens) {
                break;
            }
            if (!result.isEmpty()) {
                result.append("\n\n");
            }
            result.append("[").append(chunk.getTitle()).append("] ")
                  .append(chunk.getContent());
            currentTokens += chunkTokens;
        }
        return result.toString();
    }

    /**
     * 简单 Token 估算：中文按字数，英文按字母数字串分词
     */
    public int estimateTokens(String text) {
        if (text == null || text.isEmpty()) return 0;
        int chineseChars = 0;
        int englishWords = 0;
        Matcher wordMatcher = Pattern.compile("[a-zA-Z0-9]+").matcher(text);
        while (wordMatcher.find()) {
            englishWords++;
        }
        for (char c : text.toCharArray()) {
            if (Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS) {
                chineseChars++;
            }
        }
        int otherChars = Math.max(0, text.replaceAll("[\\u4e00-\\u9fff]", "")
            .replaceAll("[a-zA-Z0-9]", "")
            .replaceAll("\\s", "")
            .length());
        return chineseChars + englishWords + otherChars;
    }
}