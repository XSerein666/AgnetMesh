package com.agentmesh.core.protocol;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * /a2a/run 请求体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskRequest {
    /** 要调用的 Tool ID */
    private String skillId;
    /** Tool 输入参数 */
    private Map<String, Object> input;
}