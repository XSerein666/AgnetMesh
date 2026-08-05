package com.agentmesh.core.remote;

import com.agentmesh.core.protocol.AgentSkill;
import com.agentmesh.core.tool.Tool;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * 远程工具：将远程 Agent 的 Skill 包装为本地 Tool 接口。
 * 工具 ID 格式：{agentId}/{skillId}，agentId 不得包含 "/"。
 */
@Slf4j
public class RemoteTool implements Tool<Map<String, Object>, Object> {

    private final String id;
    private final AgentSkill skill;
    private final String agentUrl;
    private final AgentClient client;

    public RemoteTool(String agentId, AgentSkill skill, String agentUrl, AgentClient client) {
        this.id = agentId + "/" + skill.getId();
        this.skill = skill;
        this.agentUrl = agentUrl;
        this.client = client;
    }

    @Override
    public String getId() { return id; }

    @Override
    public String getDescription() {
        return "[" + skill.getAgentId() + "] " + skill.getDescription();
    }

    @Override
    public Map<String, Object> getInputSchema() { return skill.getInputSchema(); }

    @Override
    public Object execute(Map<String, Object> input) {
        log.info("[RemoteTool] 调用远程工具: {} @ {}", id, agentUrl);
        return client.callSkill(agentUrl, skill.getId(), input);
    }
}