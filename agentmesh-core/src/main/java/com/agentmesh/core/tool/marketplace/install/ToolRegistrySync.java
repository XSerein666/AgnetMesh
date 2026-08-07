package com.agentmesh.core.tool.marketplace.install;

import com.agentmesh.core.tool.marketplace.model.ToolVersion;

/**
 * 工具注册表同步器。
 * 负责将本地 ToolRegistry 与市场安装的工具保持同步。
 */
public interface ToolRegistrySync {

    /**
     * 启动时同步：从市场加载已安装工具并注册到 ToolRegistry。
     */
    void syncOnStartup();

    /**
     * 安装工具后同步到 ToolRegistry。
     */
    void syncAfterInstall(String toolId, ToolVersion version);

    /**
     * 卸载工具后从 ToolRegistry 移除。
     */
    void syncAfterUninstall(String toolId);

    /**
     * 从远程 Agent 同步工具列表。
     */
    void syncFromRemoteAgent(String agentId, String endpointUrl);
}
