package com.agentmesh.core.llm;

import lombok.Getter;

/**
 * 工具调用策略
 * 使用类而非枚举，以便 SPECIFIC 模式携带目标工具名
 */
@Getter
public class ToolChoice {

    private static final String TYPE_AUTO = "auto";
    private static final String TYPE_REQUIRED = "required";
    private static final String TYPE_NONE = "none";
    private static final String TYPE_SPECIFIC = "specific";

    private final String type;
    private final String specificToolName; // 仅 SPECIFIC 时有值

    // 私有构造器，强制通过工厂方法创建
    private ToolChoice(String type, String specificToolName) {
        this.type = type;
        this.specificToolName = specificToolName;
    }

    /** 由 LLM 自行决定是否调用工具 */
    public static ToolChoice auto() {
        return new ToolChoice(TYPE_AUTO, null);
    }

    /** 强制必须调用工具 */
    public static ToolChoice required() {
        return new ToolChoice(TYPE_REQUIRED, null);
    }

    /** 不调用工具，纯文本回复 */
    public static ToolChoice none() {
        return new ToolChoice(TYPE_NONE, null);
    }

    /** 强制调用指定工具 */
    public static ToolChoice specific(String toolName) {
        return new ToolChoice(TYPE_SPECIFIC, toolName);
    }

    public boolean isAuto() {
        return TYPE_AUTO.equals(this.type);
    }
    public boolean isRequired() {
        return TYPE_REQUIRED.equals(this.type);
    }
    public boolean isNone() {
        return TYPE_NONE.equals(this.type);
    }
    public boolean isSpecific() {
        return TYPE_SPECIFIC.equals(this.type);
    }
}