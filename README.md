# AgentMesh

**Java 生态的多 Agent 智能协作框架** — 不止于 Tool Calling，而是让多个 Agent 智能路由、并行协作、容错转移。

[![Java](https://img.shields.io/badge/Java-21-blue)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.5-green)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/License-Apache%202.0-orange)](LICENSE)

## 为什么选择 AgentMesh？

| 能力 | Spring AI | LangChain4j | AgentScope | **AgentMesh** |
|------|:---:|:---:|:---:|:---:|
| 单 Agent + Tool Calling | ✅ | ✅ | ✅ | ✅ |
| 多 Agent 编排 | 基础 | 实验性 | 基础 | **顺序/并行/条件** |
| 智能路由 | ❌ | ❌ | ❌ | **两阶段 LLM + A/B 测试** |
| Failover 故障转移 | ❌ | ❌ | ❌ | **异常类型判断自动切换** |
| 路由缓存 | ❌ | ❌ | ❌ | **LRU + TTL + SHA-256** |
| A2A 协议 | ❌ | ❌ | ✅ | ✅ |
| 远程工具发现 | ❌ | ❌ | ❌ | **自动拉取注册** |
| 可观测性 | 基础 | 需自建 | 基础 | **P50/P95/P99 + traceId** |

## 快速开始

### 环境要求

- JDK 21+
- Maven 3.9+

### 1. 添加依赖

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.github.xserein666</groupId>
            <artifactId>agentmesh-bom</artifactId>
            <version>1.0.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <dependency>
        <groupId>io.github.xserein666</groupId>
        <artifactId>agentmesh-core</artifactId>
    </dependency>
</dependencies>
```

### 2. 配置

```yaml
dashscope:
  api-key: ${DASHSCOPE_API_KEY}   # 阿里云百炼 API Key

agentmesh:
  registry:
    self:
      agent-id: my-agent
      name: "我的智能助手"
      description: "一个支持天气查询和知识检索的 Agent"
      version: "1.0.0"
      url: http://localhost:8080
  routing:
    strategy: keyword   # keyword | llm | ab
```

### 3. 定义 Agent 和工具

```java
@Configuration
public class AgentConfig {

    @Bean
    public Tool weatherTool() {
        return Tool.of("weather", "查询指定城市的天气",
            input -> {
                String city = (String) input.get("city");
                return Map.of("result", city + "：晴，25°C");
            });
    }

    @Bean
    public com.agentmesh.core.agent.AgentConfig myAgent() {
        return com.agentmesh.core.agent.AgentConfig.builder()
            .agentId("weather-agent")
            .description("天气查询助手，可以查询全国各城市的天气")
            .systemPrompt("你是一个专业的天气助手，请根据用户需求查询天气。")
            .routingTags(List.of("天气", "温度", "降雨"))
            .retryable(true)
            .build();
    }
}
```

### 4. 运行 Demo

> 示例代码仅在 GitHub 仓库中提供，不发布到 Maven Central。

```bash
git clone https://github.com/XSerein666/AgnetMesh.git
cd AgentMesh/agentmesh-examples/agentmesh-demo
set DASHSCOPE_API_KEY=sk-your-key
mvn spring-boot:run
```

然后访问 `http://localhost:8080/chat?msg=北京今天天气怎么样`

## 核心能力

### 多 Agent 编排

```java
// 顺序编排：Agent1 → Agent2 → Agent3
AgentOrchestrator orch = new SequentialAgentOrchestrator(factory, agentClient);
OrchestrationResult result = orch.orchestrate(plan, "帮我规划北京三日游");

// 并行编排：多个 Agent 同时执行
AgentOrchestrator orch = new ParallelOrchestrator(factory, agentClient, metrics);

// 条件路由 + failover：LLM 选最优 Agent，失败自动切换
AgentOrchestrator orch = new ConditionalOrchestrator(factory, agentClient, routingStrategy);
```

### 智能路由

```yaml
agentmesh:
  routing:
    strategy: llm     # 切换到 LLM 路由
    llm:
      top-k: 5        # 阶段1粗筛保留 Top-5
      confidence-threshold: 0.6   # 低于此阈值回退关键词路由
      timeout: 5s     # LLM 调用超时
      cache:
        enabled: true # 启用路由缓存
        max-size: 100
        ttl: 300s
```

LLM 路由的工作流程：
1. **阶段1 粗筛**：关键词匹配元数据，轻量打分，选出 Top-K 候选
2. **阶段2 精排**：将候选的 skills 描述传入 LLM，结构化输出排序结果
3. **缓存优化**：相同输入 + 相同候选集命中缓存，省去 LLM 调用
4. **低置信度回退**：榜首置信度低于阈值时，回退到关键词路由
5. **A/B 测试**：生产流量走主策略，LLM 路由同步执行记录对比指标

### 故障转移

```java
// 标记 Agent 可重试，失败时自动切换到下一个
AgentConfig.builder()
    .agentId("primary-agent")
    .retryable(true)    // 启用 failover
    .build();

// 仅对可重试错误触发：TimeoutException / ConnectException / 5xx
// 流中途失败不触发，避免重复执行
```

### 可观测性

所有指标自动注册到 Micrometer，支持 Prometheus 抓取：

| 指标 | 说明 |
|------|------|
| `agentmesh.agent.invocations` | Agent 调用次数（tag: agent_id, status） |
| `agentmesh.agent.latency` | Agent 延迟 P50/P95/P99 |
| `agentmesh.tool.executions` | 工具执行次数（tag: tool_id, status） |
| `agentmesh.tool.latency` | 工具延迟分布 |
| `agentmesh.remote.calls` | 远程调用次数（tag: agent_id, status） |
| `agentmesh.remote.latency` | 远程调用延迟 |
| `agentmesh.llm.calls` | LLM 调用次数（tag: provider） |
| `agentmesh.llm.tokens` | LLM Token 消耗 |
| `agentmesh.routing.recall` | 粗筛命中率 |
| `agentmesh.routing.rerank` | 精排结果分类 |
| `agentmesh.routing.cache` | 缓存命中率 |
| `agentmesh.routing.ab.consistency` | A/B 测试 top-1 一致率 |

## 模块结构

```
agentmesh/
├── agentmesh-bom/          # 版本管理
├── agentmesh-core/         # 核心框架
│   ├── agent/              # Agent 定义与编排引擎
│   ├── routing/            # 路由策略（关键词/LLM/A/B）
│   ├── llm/                # LLM 客户端与适配器
│   ├── tool/               # 工具抽象与注册中心
│   ├── remote/             # 远程 Agent 调用与工具发现
│   ├── protocol/           # A2A 协议（AgentCard/Task/SSE）
│   ├── registry/           # Agent 注册中心
│   ├── infrastructure/     # 可观测性（指标/traceId）
│   ├── session/            # 会话管理
│   └── task/               # 任务管理
├── agentmesh-rag/          # RAG 知识库模块（仅 GitHub，Beta 阶段）
└── agentmesh-examples/     # 示例项目（仅 GitHub）
    └── agentmesh-demo/     # 快速体验 Demo
```

## 对比同类框架

| 维度 | AgentMesh | Spring AI | LangChain4j | AgentScope |
|------|:---:|:---:|:---:|:---:|
| 定位 | 多 Agent 智能协作 | LLM 调用封装 | LLM 应用编排 | 单 Agent 自主决策 |
| 智能路由 | **两阶段 LLM + A/B** | ❌ | ❌ | ❌ |
| Failover | **异常类型判断** | ❌ | ❌ | ❌ |
| 路由缓存 | **LRU + TTL** | ❌ | ❌ | ❌ |
| 多 Agent 编排 | 顺序/并行/条件 | 基础 | 实验性 | 基础 |
| A2A 协议 | ✅ | ❌ | ❌ | ✅ |
| MCP 协议 | 计划中 | ❌ | ❌ | ✅ |
| 安全沙箱 | 计划中 | ❌ | ❌ | ✅ |
| LLM 适配 | DashScope/OpenAI/Ollama/DeepSeek | 多模型 | 多模型 | 百炼为主 |

## License

Apache 2.0 — 详见 [LICENSE](LICENSE)