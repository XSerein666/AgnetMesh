package com.jewel.a2a.server.config;

import com.agentmesh.core.agent.AgentConfig;
import com.agentmesh.core.agent.ReActAgent;
import com.agentmesh.core.agent.SequentialAgentOrchestrator;
import com.agentmesh.core.llm.LlmClient;
import com.agentmesh.core.prompt.PromptTemplateEngine;
import com.agentmesh.core.prompt.TokenBudgetManager;
import com.agentmesh.core.tool.Tool;
import com.agentmesh.core.tool.ToolRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AgentMeshConfig Bean 创建集成测试。
 * 验证所有 AgentMesh 核心组件的 Bean 创建和注入是否正确。
 */
@SpringBootTest(classes = {AgentMeshConfig.class, TestMockConfig.class, TestToolConfig.class})
@Import(TestMockConfig.class)
@DisplayName("AgentMeshConfig Bean 创建集成测试")
class AgentMeshConfigTest {

    @Autowired
    private TokenBudgetManager tokenBudgetManager;

    @Autowired
    private PromptTemplateEngine promptTemplateEngine;

    @Autowired
    private ToolRegistry agentMeshToolRegistry;

    @Autowired
    private ReActAgent reActAgent;

    @Autowired
    private SequentialAgentOrchestrator sequentialAgentOrchestrator;

    @Autowired
    @Qualifier("designerAgent")
    private AgentConfig designerAgent;

    @Autowired
    @Qualifier("crafterAgent")
    private AgentConfig crafterAgent;

    @Autowired
    @Qualifier("auditorAgent")
    private AgentConfig auditorAgent;

    @Autowired
    private LlmClient llmClient;

    // ========== TokenBudgetManager ==========

    @Nested
    @DisplayName("TokenBudgetManager")
    class TokenBudgetManagerTests {

        @Test
        @DisplayName("应成功创建 Bean 且不为 null")
        void shouldCreateBean() {
            assertNotNull(tokenBudgetManager, "TokenBudgetManager Bean 不应为 null");
        }

        @Test
        @DisplayName("Token 估算：中文文本应正确估算")
        void shouldEstimateChineseTokens() {
            int tokens = tokenBudgetManager.estimateTokens("你好世界");
            assertTrue(tokens > 0, "中文 Token 估算应大于 0");
        }

        @Test
        @DisplayName("Token 估算：空字符串应返回 0")
        void shouldReturnZeroForEmptyString() {
            assertEquals(0, tokenBudgetManager.estimateTokens(""));
            assertEquals(0, tokenBudgetManager.estimateTokens(null));
        }

        @Test
        @DisplayName("截断：超长文本应被截断")
        void shouldTruncateLongText() {
            String longText = "测试内容。".repeat(500);
            String truncated = tokenBudgetManager.truncate(longText);
            assertNotNull(truncated);
            assertTrue(truncated.length() < longText.length(),
                    "超长文本应被截断");
            assertTrue(truncated.contains("[上下文已截断"),
                    "截断后应包含截断提示");
        }

        @Test
        @DisplayName("截断：短文本不应被截断")
        void shouldNotTruncateShortText() {
            String shortText = "短文本";
            String result = tokenBudgetManager.truncate(shortText);
            assertEquals(shortText, result, "短文本不应被截断");
        }
    }

    // ========== PromptTemplateEngine ==========

    @Nested
    @DisplayName("PromptTemplateEngine")
    class PromptTemplateEngineTests {

        @Test
        @DisplayName("应成功创建 Bean 且不为 null")
        void shouldCreateBean() {
            assertNotNull(promptTemplateEngine, "PromptTemplateEngine Bean 不应为 null");
        }

        @Test
        @DisplayName("应能渲染默认模板")
        void shouldRenderDefaultTemplate() {
            String rendered = promptTemplateEngine.render("default", null);
            assertNotNull(rendered);
            assertTrue(rendered.contains("珠宝"), "默认模板应包含'珠宝'关键词");
        }

        @Test
        @DisplayName("应能渲染带变量的模板字符串")
        void shouldRenderStringWithVariables() {
            String template = "你好，${name}！今天是 ${current_date}。";
            String rendered = promptTemplateEngine.renderString(template, Map.of("name", "测试用户"));
            assertTrue(rendered.contains("测试用户"), "应替换 name 变量");
            assertFalse(rendered.contains("${name}"), "不应残留未替换的变量");
            assertFalse(rendered.contains("${current_date}"), "current_date 应被默认变量替换");
        }

        @Test
        @DisplayName("渲染不存在的模板应抛出异常")
        void shouldThrowForMissingTemplate() {
            assertThrows(RuntimeException.class,
                    () -> promptTemplateEngine.render("non_existent_template", null),
                    "渲染不存在的模板应抛出异常");
        }

        @Test
        @DisplayName("Token 估算方法应正常工作")
        void shouldEstimateTokens() {
            int tokens = promptTemplateEngine.estimateTokens("测试文本");
            assertTrue(tokens >= 0, "Token 估算不应为负数");
        }
    }

    // ========== ToolRegistry ==========

    @Nested
    @DisplayName("ToolRegistry")
    class ToolRegistryTests {

        @Test
        @DisplayName("应成功创建 Bean 且不为 null")
        void shouldCreateBean() {
            assertNotNull(agentMeshToolRegistry, "ToolRegistry Bean 不应为 null");
        }

        @Test
        @DisplayName("应包含测试 Tool")
        void shouldContainTestTools() {
            List<String> toolIds = agentMeshToolRegistry.getAllToolIds();
            assertFalse(toolIds.isEmpty(), "应至少注册一个测试工具");
            assertTrue(toolIds.contains("test_tool"), "应包含 test_tool");
        }

        @Test
        @DisplayName("执行已注册的 Tool 应返回正确结果")
        void shouldExecuteRegisteredTool() {
            Object result = agentMeshToolRegistry.execute("test_tool", Map.of("input", "hello"));
            assertNotNull(result);
            assertTrue(result instanceof Map);
            @SuppressWarnings("unchecked")
            Map<String, Object> resultMap = (Map<String, Object>) result;
            assertTrue(resultMap.containsKey("result"));
            assertEquals("executed: hello", resultMap.get("result"));
        }

        @Test
        @DisplayName("执行不存在的 Tool 应返回错误")
        void shouldReturnErrorForUnknownTool() {
            Object result = agentMeshToolRegistry.execute("unknown_tool", Map.of());
            assertNotNull(result);
            assertTrue(result instanceof Map);
            @SuppressWarnings("unchecked")
            Map<String, Object> resultMap = (Map<String, Object>) result;
            assertTrue(resultMap.containsKey("error"), "应返回 error 字段");
            assertTrue(resultMap.get("error").toString().contains("不存在"),
                    "错误消息应包含'不存在'");
        }

        @Test
        @DisplayName("校验输入：缺少必填参数应返回错误")
        void shouldValidateRequiredField() {
            List<String> errors = agentMeshToolRegistry.validateInput("test_tool", Map.of());
            assertFalse(errors.isEmpty(), "缺少必填参数应返回校验错误");
            assertTrue(errors.get(0).contains("input"), "错误消息应包含字段名");
        }

        @Test
        @DisplayName("toDefinitions 应返回非空列表")
        void shouldReturnDefinitions() {
            assertFalse(agentMeshToolRegistry.toDefinitions().isEmpty(),
                    "工具定义列表不应为空");
        }

        @Test
        @DisplayName("toDefinitionsJson 应返回合法 JSON")
        void shouldReturnValidJson() {
            String json = agentMeshToolRegistry.toDefinitionsJson();
            assertNotNull(json);
            assertTrue(json.startsWith("["), "应返回 JSON 数组");
            assertTrue(json.contains("test_tool"), "应包含工具名");
        }
    }

    // ========== ReActAgent ==========

    @Nested
    @DisplayName("ReActAgent")
    class ReActAgentTests {

        @Test
        @DisplayName("应成功创建 Bean 且不为 null")
        void shouldCreateBean() {
            assertNotNull(reActAgent, "ReActAgent Bean 不应为 null");
        }

        @Test
        @DisplayName("应能执行基本推理")
        void shouldExecuteBasicReasoning() {
            ReActAgent.AgentResult result = reActAgent.run(
                    "你是一个助手。", "你好", List.of());
            assertNotNull(result);
            assertNotNull(result.reply, "回复不应为 null");
            assertFalse(result.reply.isEmpty(), "回复不应为空");
        }
    }

    // ========== SequentialAgentOrchestrator ==========

    @Nested
    @DisplayName("SequentialAgentOrchestrator")
    class SequentialAgentOrchestratorTests {

        @Test
        @DisplayName("应成功创建 Bean 且不为 null")
        void shouldCreateBean() {
            assertNotNull(sequentialAgentOrchestrator,
                    "SequentialAgentOrchestrator Bean 不应为 null");
        }
    }

    // ========== Agent 角色定义 ==========

    @Nested
    @DisplayName("Agent 角色定义（设计师/工艺师/审核员）")
    class AgentConfigTests {

        @Test
        @DisplayName("设计师 Agent 应正确配置")
        void shouldConfigureDesignerAgent() {
            assertNotNull(designerAgent);
            assertEquals("designer", designerAgent.getAgentId());
            assertEquals("designer", designerAgent.getRole());
            assertEquals("designer", designerAgent.getPromptTemplate());
            assertEquals(5, designerAgent.getMaxLoops());
            assertNotNull(designerAgent.getDescription());
            assertNotNull(designerAgent.getRoutingTags());
            assertFalse(designerAgent.getRoutingTags().isEmpty());
            assertTrue(designerAgent.getRoutingTags().contains("设计"));
            assertTrue(designerAgent.getRoutingTags().contains("戒指"));
        }

        @Test
        @DisplayName("工艺师 Agent 应正确配置")
        void shouldConfigureCrafterAgent() {
            assertNotNull(crafterAgent);
            assertEquals("crafter", crafterAgent.getAgentId());
            assertEquals("crafter", crafterAgent.getRole());
            assertEquals("crafter", crafterAgent.getPromptTemplate());
            assertEquals(5, crafterAgent.getMaxLoops());
            assertNotNull(crafterAgent.getRoutingTags());
            assertTrue(crafterAgent.getRoutingTags().contains("工艺"));
            assertTrue(crafterAgent.getRoutingTags().contains("材质"));
        }

        @Test
        @DisplayName("审核员 Agent 应正确配置")
        void shouldConfigureAuditorAgent() {
            assertNotNull(auditorAgent);
            assertEquals("auditor", auditorAgent.getAgentId());
            assertEquals("auditor", auditorAgent.getRole());
            assertEquals("auditor", auditorAgent.getPromptTemplate());
            assertEquals(3, auditorAgent.getMaxLoops());
            assertNotNull(auditorAgent.getRoutingTags());
            assertTrue(auditorAgent.getRoutingTags().contains("审核"));
            assertTrue(auditorAgent.getRoutingTags().contains("评审"));
        }

        @Test
        @DisplayName("resolveSystemPrompt 应能渲染系统提示词")
        void shouldResolveSystemPrompt() {
            String prompt = designerAgent.resolveSystemPrompt();
            assertNotNull(prompt);
            assertFalse(prompt.isEmpty());
            assertTrue(prompt.contains("珠宝设计师") || prompt.contains("珠宝"),
                    "设计师提示词应包含珠宝相关内容");
        }
    }
}