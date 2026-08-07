package com.jewel.a2a.server.service;

import com.jewel.a2a.common.dto.AgentCard;
import com.jewel.a2a.common.dto.AgentSkill;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 构建 Agent 名片
 */
@Service
public class AgentCardService {

    public AgentCard buildAgentCard() {
        return AgentCard.builder()
                .agentId("jewel-a2a")
                .name("Jewel-A2A 珠宝定制智能体")
                .description("珠宝定制灵感生成与工艺可行性校验智能体")
                .version("1.0.0")
                .skills(buildSkills())
                .build();
    }

    private List<AgentSkill> buildSkills() {
        return List.of(
                AgentSkill.builder()
                        .id("analyze_jewelry_image")
                        .name("珠宝图片解析")
                        .description("分析珠宝图片，提取设计参数（主石、材质、工艺等）")
                        .inputSchema(Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "imageUrl", Map.of("type", "string", "description", "图片URL或Base64")
                                )
                        ))
                        .build(),
                AgentSkill.builder()
                        .id("generate_jewelry_design")
                        .name("珠宝设计生成")
                        .description("根据设计参数生成专业级珠宝设计图")
                        .inputSchema(Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "params", Map.of("type", "object", "description", "结构化设计参数"),
                                        "prompt", Map.of("type", "string", "description", "自然语言描述")
                                )
                        ))
                        .build(),
                AgentSkill.builder()
                        .id("check_craft_feasibility")
                        .name("工艺可行性校验")
                        .description("校验设计方案的物理与工艺可行性，指出缺陷并给出建议")
                        .inputSchema(Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "imageUrl", Map.of("type", "string", "description", "待校验的设计图URL")
                                )
                        ))
                        .build(),
                AgentSkill.builder()
                        .id("search_craft_knowledge")
                        .name("工艺知识检索")
                        .description("检索珠宝工艺知识库，获取工艺规范和历史案例参考")
                        .inputSchema(Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "query", Map.of("type", "string", "description", "检索关键词或问题描述")
                                )
                        ))
                        .build()
        );
    }
}