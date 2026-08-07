package com.agentmesh.core.llm;

import lombok.Data;
import java.util.Map;

/**
 * 原生 function calling 返回的工具调用请求
 */
@Data
public class ToolCallRequest {
    /** 工具调用唯一 ID */
    private String id;
    /** 工具名称 */
    private String name;
    /** 调用参数 */
    private Map<String, Object> arguments;
}
