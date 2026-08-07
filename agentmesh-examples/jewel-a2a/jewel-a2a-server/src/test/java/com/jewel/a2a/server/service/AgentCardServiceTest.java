package com.jewel.a2a.server.service;

import com.agentmesh.core.protocol.AgentCard;
import com.agentmesh.core.protocol.AgentSkill;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AgentCardService Bean 测试：验证 AgentCard 和 Skills 配置正确性。
 */
@DisplayName("AgentCardService Bean 测试")
class AgentCardServiceTest {

    private final AgentCardService agentCardService = new AgentCardService();

    // ========== AgentCard ==========

    @Nested
    @DisplayName("AgentCard 基本属性")
    class AgentCardProperties {

        @Test
        @DisplayName("agentId 应为 jewel-a2a")
        void shouldHaveCorrectAgentId() {
            AgentCard card = agentCardService.agentCard();

            assertEquals("jewel-a2a", card.getAgentId());
        }

        @Test
        @DisplayName("agentId 不应包含 '/' 字符")
        void shouldNotContainSlash() {
            AgentCard card = agentCardService.agentCard();

            assertFalse(card.getAgentId().contains("/"),
                    "agentId 不应包含 '/' 字符");
        }

        @Test
        @DisplayName("name 应包含珠宝相关描述")
        void shouldContainJewelryDescription() {
            AgentCard card = agentCardService.agentCard();

            assertTrue(card.getName().contains("珠宝"),
                    "名称应包含'珠宝'");
        }

        @Test
        @DisplayName("version 应为 2.0.0")
        void shouldHaveVersion200() {
            AgentCard card = agentCardService.agentCard();

            assertEquals("2.0.0", card.getVersion());
        }

        @Test
        @DisplayName("url 不应为 null")
        void shouldHaveUrl() {
            AgentCard card = agentCardService.agentCard();

            assertNotNull(card.getUrl());
        }

        @Test
        @DisplayName("description 应包含多 Agent 协作描述")
        void shouldDescribeMultiAgentCollaboration() {
            AgentCard card = agentCardService.agentCard();

            assertTrue(card.getDescription().contains("多 Agent"),
                    "描述应提及多 Agent 协作");
        }
    }

    // ========== Skills ==========

    @Nested
    @DisplayName("Skills 列表")
    class Skills {

        private final List<AgentSkill> skills = agentCardService.agentCard().getSkills();

        @Test
        @DisplayName("应包含 4 个 skill")
        void shouldHaveFourSkills() {
            assertEquals(4, skills.size());
        }

        @Test
        @DisplayName("应包含 analyze_jewelry_image skill")
        void shouldHaveAnalyzeImageSkill() {
            assertTrue(skills.stream().anyMatch(s -> "analyze_jewelry_image".equals(s.getId())));
        }

        @Test
        @DisplayName("应包含 generate_jewelry_design skill")
        void shouldHaveGenerateDesignSkill() {
            assertTrue(skills.stream().anyMatch(s -> "generate_jewelry_design".equals(s.getId())));
        }

        @Test
        @DisplayName("应包含 check_craft_feasibility skill")
        void shouldHaveCheckCraftSkill() {
            assertTrue(skills.stream().anyMatch(s -> "check_craft_feasibility".equals(s.getId())));
        }

        @Test
        @DisplayName("应包含 search_craft_knowledge skill")
        void shouldHaveSearchCraftKnowledgeSkill() {
            assertTrue(skills.stream().anyMatch(s -> "search_craft_knowledge".equals(s.getId())));
        }

        @Test
        @DisplayName("每个 skill 应有 inputSchema")
        void shouldHaveInputSchemaForEachSkill() {
            for (AgentSkill skill : skills) {
                assertNotNull(skill.getInputSchema(),
                        "Skill " + skill.getId() + " 缺少 inputSchema");
                assertEquals("object", skill.getInputSchema().get("type"));
            }
        }

        @Test
        @DisplayName("每个 skill 应有 name 和 description")
        void shouldHaveNameAndDescription() {
            for (AgentSkill skill : skills) {
                assertNotNull(skill.getName(), "Skill " + skill.getId() + " 缺少 name");
                assertNotNull(skill.getDescription(), "Skill " + skill.getId() + " 缺少 description");
                assertFalse(skill.getName().isEmpty());
                assertFalse(skill.getDescription().isEmpty());
            }
        }
    }
}