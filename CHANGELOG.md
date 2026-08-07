# Changelog

All notable changes to AgentMesh will be documented in this file.

## [1.1.0] - Unreleased

### Added

- 记忆管理：滑动窗口记忆（SlidingWindowMemoryManager）+ 向量存储（InMemoryVectorStore）
- 任务规划：LLM 任务分解（LlmTaskPlanner）+ DAG 执行引擎（DagPlanExecutor）
- 多 Agent 协作：Swarm 协作、Debate 辩论、Supervisor-Worker 监督模式
- 工具市场：工具安装/卸载、健康检查、版本管理、审批策略、MCP 工具执行器
- SequentialAgentOrchestrator：基于 Prompt 模板的多 Agent 串联流水线
- ConditionalOrchestrator：条件路由编排器
- 消息总线：Agent 间消息传递（InMemoryMessageBus）
- 工作流状态持久化（FileWorkflowStateStore）
- 审批门控（ApprovalGate）
- 知识块管理（KnowledgeChunk）
- Token 预算管理器（TokenBudgetManager）

## [1.0.0] - Unreleased

### Added

- 多 Agent 编排引擎：顺序编排（Sequential）、并行编排（Parallel）、条件路由编排（Conditional）
- 两阶段 LLM 智能路由：阶段1 关键词粗筛 + 阶段2 LLM 精排，支持 A/B 测试
- 故障转移（Failover）：基于异常类型自动切换到备用 Agent
- 路由缓存：LRU + TTL，支持 SHA-256 缓存键
- A2A 协议支持：AgentCard、AgentSkill、Task、Chat、SSE 流式响应
- 远程 Agent 调用与工具自动发现（RemoteToolRegistrar）
- 4 家 LLM 适配：DashScope（阿里云百炼）、OpenAI、Ollama、DeepSeek
- 可观测性：P50/P95/P99 延迟分布 + traceId 全链路追踪，对接 Micrometer/Prometheus
- ReAct Agent：基于 LLM function calling 的推理循环
- Prompt 模板引擎：支持模板变量替换、Token 预算管理
- 会话管理：内存和 JDBC 两种实现
- 任务管理：支持异步任务提交与轮询，乐观锁防并发冲突
- Agent 注册中心：内存实现，支持动态刷新和远程 Agent 导入
- Tool 抽象：Tool 定义、Schema 校验、权限控制、ToolRegistry
- Spring Boot 3.3.5 + JDK 21 支持