# 贡献指南

感谢你对 AgentMesh 的关注！我们欢迎任何形式的贡献，包括但不限于代码、文档、Bug 报告、功能建议。

## 行为准则

本项目遵循 [Contributor Covenant 行为准则](CODE_OF_CONDUCT.md)。参与即表示你同意遵守其条款。

## 如何贡献

### 报告 Bug

1. 在 [Issues](https://github.com/XSerein666/AgnetMesh/issues) 中搜索，确认 Bug 未被报告过
2. 使用 Bug Report 模板创建 Issue，包含：
   - 复现步骤
   - 期望行为 vs 实际行为
   - 环境信息（JDK 版本、Spring Boot 版本、操作系统）

### 提出功能建议

1. 在 Issues 中搜索，确认该功能未被提议过
2. 使用 Feature Request 模板创建 Issue，说明：
   - 解决什么问题
   - 建议的 API 或使用方式
   - 是否有替代方案

### 提交代码

1. **Fork 仓库** 并 clone 到本地
2. **创建分支**：`feature/xxx` 或 `fix/xxx`
3. **编写代码**，遵循项目编码规范
4. **添加测试**，确保覆盖率不下降
5. **运行测试**：`mvn verify`
6. **提交 PR**，使用 PR 模板，关联相关 Issue

## 开发环境

- JDK 21+
- Maven 3.9+

```bash
# 编译
mvn compile

# 运行测试
mvn test

# 完整验证（含 checkstyle）
mvn verify
```

## 编码规范

### Java 代码风格

- 遵循标准的 Java 命名规范
- 使用 Lombok 简化 POJO（`@Data`、`@Builder`、`@Slf4j`）
- 公开 API 必须添加 Javadoc 注释
- 配置类使用 `@Bean` 方法注册组件，而非 `@Component` 注解

### 提交信息规范

```
<type>: <简短描述>

类型：
  feat     新功能
  fix      Bug 修复
  docs     文档更新
  refactor 代码重构
  test     测试相关
  chore    构建/工具链
```

示例：
```
feat: 添加 MCP 协议支持
fix: 修复 RemoteToolRegistrar 并发注册问题
docs: 更新路由策略配置说明
```

### 测试要求

- 新增功能必须包含单元测试
- Bug 修复应包含回归测试
- 测试类命名：`{ClassName}Test.java`
- 使用 JUnit 5 + Mockito

## 项目结构

```
agentmesh/
├── agentmesh-bom/          # 版本管理
├── agentmesh-core/         # 核心框架
│   ├── agent/              # Agent 定义与编排
│   ├── routing/            # 路由策略
│   ├── llm/                # LLM 客户端与适配器
│   ├── tool/               # 工具抽象与注册
│   ├── remote/             # 远程调用与工具发现
│   ├── protocol/           # A2A 协议
│   ├── registry/           # Agent 注册中心
│   ├── infrastructure/     # 可观测性
│   ├── session/            # 会话管理
│   ├── task/               # 任务管理
│   └── prompt/             # Prompt 模板
├── agentmesh-rag/          # RAG 知识库模块
└── agentmesh-examples/     # 示例项目
```

## 获取帮助

- 在 [Issues](https://github.com/XSerein666/AgnetMesh/issues) 中提问
- 在 [Discussions](https://github.com/XSerein666/AgnetMesh/discussions) 中讨论