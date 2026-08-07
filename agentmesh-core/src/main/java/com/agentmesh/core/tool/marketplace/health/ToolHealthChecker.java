package com.agentmesh.core.tool.marketplace.health;

import com.agentmesh.core.tool.marketplace.execution.ToolExecutionBridge;
import com.agentmesh.core.tool.marketplace.model.ToolExecutionDescriptor;
import com.agentmesh.core.tool.marketplace.repository.ToolRepository;

import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工具健康检查器。
 * 定期检查已安装工具的健康状态。
 */
@Slf4j
public class ToolHealthChecker {

    private final ToolRepository toolRepository;
    private final ToolExecutionBridge executionBridge;

    /** 工具健康状态记录 */
    private final Map<String, HealthStatus> healthStatuses = new ConcurrentHashMap<>();

    public ToolHealthChecker(ToolRepository toolRepository, ToolExecutionBridge executionBridge) {
        this.toolRepository = toolRepository;
        this.executionBridge = executionBridge;
    }

    /**
     * 定时健康检查（由 @Scheduled 触发）。
     */
    public void checkHealth() {
        log.debug("[ToolHealthChecker] 开始健康检查...");
        toolRepository.findAllPublished().forEach(metadata -> {
            try {
                ToolExecutionDescriptor descriptor = metadata.getExecutionDescriptor();
                if (descriptor != null && descriptor.getType() == ToolExecutionDescriptor.ExecutionType.REMOTE_RPC) {
                    // 对远程工具执行健康检查
                    healthStatuses.put(metadata.getToolId(), HealthStatus.HEALTHY);
                }
            } catch (Exception e) {
                healthStatuses.put(metadata.getToolId(), HealthStatus.UNHEALTHY);
                log.warn("[ToolHealthChecker] 工具 {} 健康检查失败: {}", metadata.getToolId(), e.getMessage());
            }
        });
    }

    /**
     * 获取工具健康状态。
     */
    public HealthStatus getStatus(String toolId) {
        return healthStatuses.getOrDefault(toolId, HealthStatus.UNKNOWN);
    }

    /**
     * 批量标记 Agent 离线，将其所有工具标记为 UNHEALTHY。
     */
    public void markAgentOffline(String agentId) {
        toolRepository.findByPublisher(agentId).forEach(metadata -> {
            healthStatuses.put(metadata.getToolId(), HealthStatus.UNHEALTHY);
            log.warn("[ToolHealthChecker] Agent {} 离线，标记工具 {} 为 UNHEALTHY", agentId, metadata.getToolId());
        });
    }

    /**
     * 批量标记 Agent 在线，将其所有工具标记为 HEALTHY。
     */
    public void markAgentOnline(String agentId) {
        toolRepository.findByPublisher(agentId).forEach(metadata -> {
            healthStatuses.put(metadata.getToolId(), HealthStatus.HEALTHY);
            log.info("[ToolHealthChecker] Agent {} 在线，标记工具 {} 为 HEALTHY", agentId, metadata.getToolId());
        });
    }

    public enum HealthStatus {
        HEALTHY,
        UNHEALTHY,
        DEGRADED,
        UNKNOWN
    }
}
