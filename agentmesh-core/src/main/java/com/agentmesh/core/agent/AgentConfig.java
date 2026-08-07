package com.agentmesh.core.agent;

import com.agentmesh.core.collaboration.MessageBus;
import com.agentmesh.core.collaboration.SharedContext;
import com.agentmesh.core.llm.LlmClient;
import com.agentmesh.core.prompt.PromptTemplateEngine;
import com.agentmesh.core.protocol.AgentSkill;
import com.agentmesh.core.tool.ToolRegistry;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.List;

/**
 * Agent 配置
 */
@Data
@Builder
@Slf4j
public class AgentConfig {
    /** Agent 标识（用于编排时识别 Agent） */
    @Builder.Default
    private String agentId = "";
    /** 远程 Agent 的 HTTP 端点（非空时表示远程 Agent，编排时走 AgentClient 调用） */
    @Builder.Default
    private String agentUrl = "";
    /** 系统提示词（直接文本，优先级高于模板） */
    private String systemPrompt;
    /** 提示词模板名称（与 systemPrompt 二选一） */
    private String promptTemplate;
    /** 提示词模板引擎 */
    private PromptTemplateEngine promptEngine;
    /** LLM 客户端 */
    private LlmClient llmClient;
    /** Tool 注册中心 */
    private ToolRegistry toolRegistry;
    /** 最大推理轮数 */
    @Builder.Default
    private int maxLoops = 5;

    // ========== Phase 11：路由相关字段 ==========

    /** Agent 描述（供 LLM 路由使用，短文本） */
    private String description;
    /** 路由标签（供阶段1粗筛关键词匹配） */
    private List<String> routingTags;
    /** 路由规则（保留向后兼容，KeywordRoutingStrategy 使用） */
    private String routingRule;
    /** 是否可重试（failover 链仅对 retryable=true 的 Agent 启用），默认 false */
    @Builder.Default
    private boolean retryable = false;
    /** Agent 的 skills 列表（供 LLM 精排渲染 name/description/inputSchema） */
    private List<AgentSkill> skills;

    // ========== Phase 1.1：多 Agent 协作字段 ==========

    /** Agent 角色（supervisor / worker / reviewer / debater）。
     *  null 表示平级 Agent（向后兼容），但无权限写入受保护的 SharedContext 前缀。 */
    @Builder.Default
    private String role = null;

    /** 可委派的 Agent ID 列表（仅 supervisor 有效） */
    @Builder.Default
    private List<String> delegateTo = Collections.emptyList();

    /** 共享上下文（多个 Agent 共享），由编排器注入 */
    private SharedContext sharedContext;

    /** 消息总线（Agent 间通信通道），由编排器注入 */
    private MessageBus messageBus;

    /**
     * 解析最终的系统提示词
     */
    public String resolveSystemPrompt() {
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            return systemPrompt;
        }
        if (promptEngine == null) {
            log.warn("[AgentConfig] 未配置 systemPrompt 且 promptEngine 为 null，使用空提示词");
            return "";
        }
        String templateToUse = (promptTemplate != null && !promptTemplate.isEmpty())
                ? promptTemplate : "default";
        return promptEngine.render(templateToUse, null);
    }

    /** 是否为远程 Agent */
    public boolean isRemote() {
        return agentUrl != null && !agentUrl.isEmpty();
    }
}
