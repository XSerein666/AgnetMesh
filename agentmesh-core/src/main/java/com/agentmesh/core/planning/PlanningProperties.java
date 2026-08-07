package com.agentmesh.core.planning;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 任务规划配置
 */
@Data
@ConfigurationProperties(prefix = "agentmesh.planning")
public class PlanningProperties {

    /** 是否启用任务规划 */
    private boolean enabled = false;

    /** 规划使用的模型 */
    private String model = "qwen-plus";

    /** 最多拆解子任务数 */
    private int maxSubtasks = 10;

    /** 总执行超时（秒） */
    private int timeout = 120;

    /** 无依赖子任务并发执行 */
    private boolean parallel = true;
}
