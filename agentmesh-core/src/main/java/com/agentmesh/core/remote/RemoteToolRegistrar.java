package com.agentmesh.core.remote;

import com.agentmesh.core.registry.AgentNode;
import com.agentmesh.core.registry.AgentRegistry;
import com.agentmesh.core.tool.ToolRegistry;
import lombok.extern.slf4j.Slf4j;

import java.util.HashSet;
import java.util.Set;

/**
 * 远程工具注册器。
 * 从 AgentRegistry 中扫描所有远程 Agent，将其技能包装为 RemoteTool 并注册到 ToolRegistry。
 * importAll() 幂等：已存在的工具会覆盖更新（确保远程技能变更后生效）。
 */
@Slf4j
public class RemoteToolRegistrar {

    private final AgentRegistry agentRegistry;
    private final AgentClient agentClient;
    private final Set<String> registeredToolIds = new HashSet<>();

    public RemoteToolRegistrar(AgentRegistry agentRegistry, AgentClient agentClient) {
        this.agentRegistry = agentRegistry;
        this.agentClient = agentClient;
    }

    /**
     * 扫描所有远程 Agent 并注册其工具到 ToolRegistry。
     * 幂等：已注册的工具重建 RemoteTool 并覆盖（ToolRegistry.register 为 put 覆盖语义）。
     */
    public void importAll(ToolRegistry toolRegistry) {
        String selfId = agentRegistry.self().getCard().getAgentId();
        int imported = 0;
        int updated = 0;

        for (AgentNode node : agentRegistry.discover()) {
            String agentId = node.getCard().getAgentId();

            // 跳过自身
            if (selfId.equals(agentId)) {
                continue;
            }
            // 校验 agentId 不含 /
            if (agentId.contains("/")) {
                log.warn("[RemoteToolRegistrar] agentId 包含 '/' 无法注册远程工具: {}", agentId);
                continue;
            }

            if (node.getCard().getSkills() == null) {
                continue;
            }

            for (var skill : node.getCard().getSkills()) {
                RemoteTool tool = new RemoteTool(agentId, skill, node.getCard().getUrl(), agentClient);
                String toolId = tool.getId();

                if (registeredToolIds.contains(toolId)) {
                    // 已存在 → 覆盖更新（远程技能可能变更了 schema 或 description）
                    toolRegistry.register(tool);
                    updated++;
                    log.info("[RemoteToolRegistrar] 更新远程工具: {}", toolId);
                } else {
                    // 首次注册
                    toolRegistry.register(tool);
                    registeredToolIds.add(toolId);
                    imported++;
                    log.info("[RemoteToolRegistrar] 注册远程工具: {} -> {} ({})",
                            toolId, agentId, skill.getDescription());
                }
            }
        }

        log.info("[RemoteToolRegistrar] 导入完成，本次新增 {} 个、更新 {} 个，累计 {} 个",
                imported, updated, registeredToolIds.size());
    }

    /** 获取已注册的远程工具数量 */
    public int getRegisteredCount() {
        return registeredToolIds.size();
    }
}