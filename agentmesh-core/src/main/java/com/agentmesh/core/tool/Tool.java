package com.agentmesh.core.tool;

import java.util.Map;

/**
 * 泛型 Tool 接口
 *
 * @param <I> 输入类型
 * @param <O> 输出类型
 */
public interface Tool<I, O> {

    /** 工具唯一标识 */
    String getId();

    /** 工具描述（供 Agent 选择调用哪个 Tool） */
    String getDescription();

    /** 输入参数 Schema（JSON Schema 格式） */
    Map<String, Object> getInputSchema();

    /** 执行工具 */
    O execute(I input);
}
