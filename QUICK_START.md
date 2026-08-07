# AgentMesh Quick Start

5 分钟跑起你的第一个多 Agent 协作应用。

## 环境要求

- **JDK 21+**
- **Maven 3.9+**
- **阿里云百炼 API Key**（[免费申请](https://bailian.console.aliyun.com/)）

## 第 1 步：克隆项目

```bash
git clone https://github.com/XSerein666/AgnetMesh.git
cd AgentMesh
```

## 第 2 步：配置 API Key

**Windows (PowerShell):**
```powershell
$env:DASHSCOPE_API_KEY = "sk-your-api-key"
```

**macOS / Linux:**
```bash
export DASHSCOPE_API_KEY=sk-your-api-key
```

## 第 3 步：编译运行

```bash
mvn clean install -DskipTests
cd agentmesh-examples/agentmesh-demo
mvn spring-boot:run
```

看到 `Started DemoApplication` 即启动成功，默认端口 **8080**。

## 第 4 步：验证效果

打开新终端，试试这些接口：

```bash
# 1. 普通对话
curl -X POST http://localhost:8080/chat \
  -H "Content-Type: application/json" \
  -d '{"message":"北京今天天气怎么样？"}'

# 2. 流式对话（SSE）
curl -N -X POST http://localhost:8080/chat/stream \
  -H "Content-Type: application/json" \
  -d '{"message":"介绍一下你自己"}'

# 3. 查看 Agent Card
curl http://localhost:8080/.well-known/agent.json

# 4. 查看注册中心
curl http://localhost:8080/registry/agents

# 5. 多 Agent 编排
curl -N -X POST http://localhost:8080/orchestrate/stream \
  -H "Content-Type: application/json" \
  -d '{"message":"帮我规划北京三日游","mode":"SEQUENTIAL"}'
```

## 第 5 步：查看 API 文档

启动后浏览器访问：

```
http://localhost:8080/swagger-ui.html
```

## 内置工具

| 工具 | 功能 | 示例 |
|------|------|------|
| `weather` | 天气查询 | "北京今天天气怎么样？" |
| `knowledge` | 知识检索 | "AgentMesh 是什么？" |
| `calculator` | 数学计算 | "计算 123 * 456" |

## 配置说明

核心配置项 (`application.yml`)：

```yaml
# LLM 配置
dashscope:
  api-key: ${DASHSCOPE_API_KEY}

# Agent 注册
agentmesh:
  registry:
    self:
      agent-id: agent-demo
      name: "AgentMesh Demo Agent"
      description: "演示 Agent"
      version: "1.0.0"
      url: http://localhost:8080

  # 路由策略: keyword | llm | ab
  routing:
    strategy: keyword

  # 远程工具超时
  remote:
    timeout: 15s
```

## 下一步

- [完整文档](https://github.com/XSerein666/AgnetMesh) — 架构设计、核心概念、API 参考
- [模块结构](#模块结构) — 了解各模块职责
- [多 Agent 编排](#多-agent-编排) — 顺序 / 并行 / 条件路由
- [智能路由](#智能路由) — 两阶段 LLM 路由 + A/B 测试
- [Swagger API](http://localhost:8080/swagger-ui.html) — 工具市场 REST API 文档

## 常见问题

**Q: 启动报 `DASHSCOPE_API_KEY` 相关错误？**  
A: 确保已正确设置环境变量 `DASHSCOPE_API_KEY`，且 Key 有效。

**Q: 如何添加自定义工具？**  
A: 在 `DemoConfig` 中添加 `@Bean Tool` 定义，参考 [DemoConfig.java](agentmesh-examples/agentmesh-demo/src/main/java/com/agentmesh/demo/DemoConfig.java)。

**Q: 如何接入其他 LLM？**  
A: AgentMesh 支持 DashScope、OpenAI、Ollama、DeepSeek，配置对应的 `LlmClient` Bean 即可。

**Q: 端口被占用？**  
A: 在 `application.yml` 中修改 `server.port: 8081`。