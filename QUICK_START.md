# AgentMesh Quick Start

5 分钟跑起你的第一个多 Agent 协作应用。

## 环境要求

- **JDK 21+**
- **Maven 3.9+**
- **阿里云百炼 API Key**（[免费申请](https://bailian.console.aliyun.com/)）

## 第 1 步：克隆项目

```bash
git clone https://github.com/XSerein666/AgentMesh.git
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
cd agentmesh-examples/jewel-a2a
mvn spring-boot:run -pl jewel-a2a-starter
```

看到 `Started JewelA2AApplication` 即启动成功，默认端口 **8080**。

## 第 4 步：验证效果

打开新终端，试试这些接口：

```bash
# 1. 单 Agent 聊天
curl -X POST http://localhost:8080/a2a/chat \
  -H "Content-Type: application/json" \
  -d '{"message":"设计一个黄金戒指"}'

# 2. 串联流水线（设计师 → 工艺师 → 审核员）
curl -X POST http://localhost:8080/a2a/chat/sequential \
  -H "Content-Type: application/json" \
  -d '{"message":"设计一个钻石项链"}'

# 3. 关键词路由
curl -X POST http://localhost:8080/a2a/chat/routed \
  -H "Content-Type: application/json" \
  -d '{"message":"检查这个工艺是否可行"}'

# 4. 查看 Agent Card
curl http://localhost:8080/.well-known/agent.json

# 5. 精确 Skill 调用
curl -X POST http://localhost:8080/a2a/run \
  -H "Content-Type: application/json" \
  -d '{"skillId":"designer/GenerateDesign","input":{"description":"黄金戒指"}}'
```

## 第 5 步：查看 API 文档

启动后浏览器访问：

```
http://localhost:8080/swagger-ui.html
```

## 内置工具

| 工具 | 所属 Agent | 功能 |
|------|-----------|------|
| `GenerateDesign` | 设计师 | 珠宝设计图生成 |
| `CheckCraft` | 工艺师 | 工艺可行性校验 |
| `SearchCraftKnowledge` | 审核员 | 工艺知识库检索 |
| `AnalyzeImage` | 设计师 | 图片分析 |

## 配置说明

核心配置项 (`application.yml`)：

```yaml
# LLM 配置
agentmesh:
  llm:
    dashscope:
      api-key: ${DASHSCOPE_API_KEY}

  # Agent 注册
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

- [完整文档](https://github.com/XSerein666/AgentMesh) — 架构设计、核心概念、API 参考
- [模块结构](https://github.com/XSerein666/AgentMesh#模块结构) — 了解各模块职责
- [多 Agent 编排](https://github.com/XSerein666/AgentMesh#多-agent-编排) — 顺序 / 并行 / 条件路由
- [智能路由](https://github.com/XSerein666/AgentMesh#智能路由) — 两阶段 LLM 路由 + A/B 测试
- [Swagger API](http://localhost:8080/swagger-ui.html) — 工具市场 REST API 文档

## 常见问题

**Q: 启动报 `DASHSCOPE_API_KEY` 相关错误？**  
A: 确保已正确设置环境变量 `DASHSCOPE_API_KEY`，且 Key 有效。

**Q: 如何添加自定义工具？**  
A: 参考 [AgentMeshConfig.java](agentmesh-examples/jewel-a2a/jewel-a2a-server/src/main/java/com/jewel/a2a/server/config/AgentMeshConfig.java) 中的 Tool 定义方式。

**Q: 如何接入其他 LLM？**  
A: AgentMesh 支持 DashScope、OpenAI、Ollama、DeepSeek，配置对应的 `LlmClient` Bean 即可。

**Q: 端口被占用？**  
A: 在 `application.yml` 中修改 `server.port: 8081`。