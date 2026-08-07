package com.agentmesh.core.protocol;

import jakarta.validation.constraints.NotBlank;
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
    /** 要调用的 Tool ID，必填 */
    @NotBlank(message = "skillId 不能为空")
    private String skillId;

    /** Tool 输入参数 */
    private Map<String, Object> input;
}
