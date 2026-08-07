package com.agentmesh.core.integration;

import com.agentmesh.core.protocol.AgentCard;
import com.agentmesh.core.protocol.AgentSkill;
import com.agentmesh.core.registry.AgentAuthProperties;
import com.agentmesh.core.registry.InMemoryAgentRegistry;
import com.agentmesh.core.remote.AgentClient;
import com.agentmesh.core.remote.HttpAgentClient;
import com.agentmesh.core.remote.RemoteToolProperties;
import com.agentmesh.core.remote.RemoteToolRegistrar;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A2A 协议集成测试：远程 Agent 发现、工具注册、远程调用。
 */
@DisplayName("A2A 协议集成测试")
class A2AProtocolIntegrationTest extends BaseIntegrationTest {

    private RemoteAgentMockServer remoteAgent;

    @BeforeEach
    void setUpRemote() {
        remoteAgent = new RemoteAgentMockServer("remote-weather-agent");
        remoteAgent.start();
    }

    @AfterEach
    void tearDownRemote() {
        if (remoteAgent != null) {
            remoteAgent.stop();
        }
    }

    @Test
    @DisplayName("远程 Agent 发现：通过 peer URL 获取 AgentCard")
    void shouldDiscoverRemoteAgent() {
        remoteAgent.stubAgentCard(
                "远程天气 Agent",
                "提供天气查询服务",
                "[{\"id\":\"get_weather\",\"name\":\"get_weather\",\"description\":\"查询天气\",\"agentId\":\"remote-weather-agent\",\"inputSchema\":{\"type\":\"object\",\"properties\":{\"city\":{\"type\":\"string\"}}}}]");

        // 通过 InMemoryAgentRegistry 发现远程 Agent
        AgentCard selfCard = new AgentCard();
        selfCard.setAgentId("test-self");
        selfCard.setName("测试 Agent");
        selfCard.setUrl("http://localhost:9999");

        InMemoryAgentRegistry registry = new InMemoryAgentRegistry(
                selfCard, new RestTemplate(), List.of(remoteAgent.baseUrl()));

        // 验证发现结果
        assertThat(registry.discover()).isNotEmpty();
        assertThat(registry.resolve("remote-weather-agent")).isNotNull();
    }

    @Test
    @DisplayName("远程工具注册：从远程 AgentCard 导入工具到本地 ToolRegistry")
    void shouldImportRemoteTools() {
        remoteAgent.stubAgentCard(
                "远程天气 Agent",
                "提供天气查询服务",
                "[{\"id\":\"remote_weather\",\"name\":\"remote_weather\",\"description\":\"远程天气查询\",\"agentId\":\"remote-weather-agent\",\"inputSchema\":{\"type\":\"object\",\"properties\":{\"city\":{\"type\":\"string\"}}}}]");

        AgentCard selfCard = new AgentCard();
        selfCard.setAgentId("test-self");
        selfCard.setName("测试 Agent");
        selfCard.setUrl("http://localhost:9999");

        InMemoryAgentRegistry registry = new InMemoryAgentRegistry(
                selfCard, new RestTemplate(), List.of(remoteAgent.baseUrl()));

        AgentClient agentClient = createAgentClient();
        RemoteToolRegistrar registrar = new RemoteToolRegistrar(registry, agentClient);
        registrar.importAll(toolRegistry);

        assertThat(toolRegistry.getAllToolIds()).contains("remote-weather-agent/remote_weather");
    }

    @Test
    @DisplayName("远程工具注册：agentId 含 '/' 应跳过并警告")
    void shouldSkipInvalidAgentId() {
        remoteAgent.stubAgentCard(
                "无效 Agent",
                "无效",
                "[{\"id\":\"test\",\"name\":\"test\",\"description\":\"test\",\"agentId\":\"invalid/agent\",\"inputSchema\":{}}]");

        AgentCard selfCard = new AgentCard();
        selfCard.setAgentId("test-self");
        selfCard.setName("测试 Agent");
        selfCard.setUrl("http://localhost:9999");

        InMemoryAgentRegistry registry = new InMemoryAgentRegistry(
                selfCard, new RestTemplate(), List.of(remoteAgent.baseUrl()));

        AgentClient agentClient = createAgentClient();
        RemoteToolRegistrar registrar = new RemoteToolRegistrar(registry, agentClient);
        registrar.importAll(toolRegistry);

        assertThat(toolRegistry.getAllToolIds().stream()
                .anyMatch(id -> id.contains("invalid/agent"))).isFalse();
    }

    @Test
    @DisplayName("远程 Agent 调用：callSkill → 同步轮询获取结果")
    void shouldCallRemoteAgentSkill() {
        remoteAgent.stubTaskSubmit("task-001");
        remoteAgent.stubTaskPolling("task-001", "北京今天晴，25°C", 0);

        AgentClient client = createAgentClient();

        Object result = client.callSkill(remoteAgent.baseUrl(), "get_weather",
                Map.of("city", "北京"));

        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("远程 Agent 超时：应正确处理超时场景")
    void shouldHandleRemoteTimeout() {
        remoteAgent.stubTimeout();

        AgentClient client = createAgentClient();

        Object result = client.callSkill(remoteAgent.baseUrl(), "get_weather",
                Map.of("city", "北京"));
        assertThat(result).isNotNull();
    }

    private AgentClient createAgentClient() {
        RemoteToolProperties properties = new RemoteToolProperties();
        properties.setTimeout(java.time.Duration.ofSeconds(5));
        properties.setPollInterval(java.time.Duration.ofSeconds(1));
        properties.setStreamTimeout(java.time.Duration.ofSeconds(30));

        return new HttpAgentClient(
                new RestTemplate(),
                WebClient.builder().build(),
                properties,
                new AgentAuthProperties(),
                "test-agent");
    }
}