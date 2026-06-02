# Agent Harness 设计原则

本文档定义 `java-tiny-claw` 的项目级架构基线。后续所有设计与实现都以本文档为准；若实现与本文档冲突，先改文档，再改代码。

## 1. 目标

- 构建一个可控、可测试、可审计体验的 Java AI Agent Harness（当前以可观测可追踪闭环为主）。
- 让大模型负责“决策”，Harness 负责“运行时控制”。
- 用清晰边界替代黑盒框架，降低上下文失控和边界失控。

## 2. 非目标

- 不追求一开始就做成“大而全”的 Agent 平台。
- 不先堆叠复杂编排、工作流引擎或多层抽象。
- 不让控制流分散到业务代码中。

## 3. 总体原则

### 3.1 Harness 优先

- 模型负责规划、推理、选择动作。
- Harness 负责主循环、约束、工具执行、运行时上下文管理与异常处理。
- 任何复杂能力都必须有明确执行边界。

### 3.2 工具极简

默认只保留最小工具集：

- `read`
- `write`
- `edit`
- `bash`

### 3.3 上下文模型（当前实现）

当前 tiny-claw 精简版不做任务持久化/恢复。

- 运行时只维护内存上下文 `AgentContext`。
- 每次运行由 CLI 输入/内存上下文重建，步进信息不跨 JVM 持久化。
- 历史上 `com.jaising.agent.state`、`StateStore`、`checkpoint` 可用于外部恢复，这些能力**不在当前实现中**。

### 3.4 安全前置

- 高危操作必须在执行前可拦截。
- 所有工具调用统一经过 Middleware。
- 越界行为必须可追踪（至少在日志与结果中留痕）。

### 3.5 观测优先

- 以可读日志为主，使用 `RunLogger` 输出关键步骤。
- CLI 非 debug 场景必须输出 `OBSERVATIONS`，用于闭环判断。
- 最终输出以 `RunResult` 为准，覆盖成功/失败、步数与观察信息。

### 3.6 接口分层

- 通过接口隔离 Provider、Tool、Middleware，避免跨层耦合。
- 实现可替换，边界不能模糊。
- 不把模型厂商能力泄漏到 Runtime，不把运行时控制放进 Provider。

## 4. 架构边界

### 4.1 Runtime

Runtime 负责主循环与控制流。

- 接收任务
- 组织消息上下文
- 调用模型
- 解析动作
- 执行工具
- 处理观测结果并推进下一步
- 决定继续或结束

Runtime 不负责具体工具实现，也不处理模型厂商协议。

### 4.2 Provider

Provider 负责模型协议适配。

- 把 `AgentContext` + `DecisionPhase` 映射为厂商请求
- 把厂商响应映射为 `Decision`
- 处理厂商错误与响应校验

Provider 不执行工具、不修改主循环状态。

### 4.3 Tool Registry

Tool Registry 负责工具声明与路由执行。

- 注册工具
- 按名查找工具
- 暴露工具定义给模型
- 路由执行并包装统一结果

### 4.4 Middleware

Middleware 负责治理与安全约束。

- 路径与参数边界
- 授权和 allow/deny
- 风险拦截与策略执行

### 4.5 StateStore（历史目标）

- 历史目标：任务计划、当前步骤、历史摘要、错误信息可持久化，并可恢复。
- 当前 tiny-claw：**不实现** `StateStore`（对应的历史分层 `com.jaising.agent.state` 已停用）。

### 4.6 Tracer（历史目标）

- 历史目标：记录结构化 trace、决策链、模型输入输出。
- 当前 tiny-claw：**不实现** `Tracer`（对应的历史分层 `com.jaising.agent.trace` 已停用）；观测由 `RunLogger` + `RunResult` 覆盖。

## 5. 默认实现策略

### 5.1 第一版形态

- 模块化单体，先把边界收紧再扩展。
- 以 CLI 场景与本地任务为主要验证对象。

### 5.2 循环模型

默认采用：

`think -> act -> observe -> decide`

其中：

- `think`：模型给出 `Decision`
- `act`：Runtime 调用 Registry 执行工具
- `observe`：Runtime 记录观察并更新 `AgentContext`
- `decide`：Runtime 判断继续、失败或结束

### 5.3 失败处理

- 工具失败要返回统一失败语义
- 连续异常和上限命中可触发中断
- 高危动作必须被拦截或返回失败

## 6. 开发约束

- 新功能必须明确落在哪一层：Runtime、Provider、Tool Registry、Middleware。
- 新工具必须先经过 Registry 与 Middleware 约束。
- 新逻辑不能把控制流散落在业务层。
- 若跨层边界变模糊，优先先重构边界再加功能。

## 7. 版本控制原则

- 本文档是实现前约束；实现发生变化先更新本文档。
- 未经文档确认，不应进入大规模实现。

## 8. 当前结论

实现顺序建议：

1. Runtime
2. Provider
3. Tool Registry
4. Middleware
5. 可观测增强（RunLogger/RunResult 继续补齐）

## 9. 技术选型

- JDK: `Java 21`
- 构建: `Maven`
- 测试: `JUnit 5`、`AssertJ`、`Mockito`
- 架构约束测试: `ArchUnit`
- 序列化: `Jackson`
- 日志: `SLF4J` + `Logback`

## 10. 代码结构基线

```text
src/main/java/com/jaising/agent
├── app          启动入口与装配
├── runtime      主循环、AgentContext、RunLogger、RunResult
├── provider     模型适配
├── tool         工具注册与执行
├── middleware   安全、白名单、循环限制
└── domain       纯数据模型
```

对应测试结构：

```text
src/test/java/com/jaising/agent
├── runtime
├── provider
├── tool
├── middleware
└── architecture
```

## 11. 单元测试基线

- `runtime`：`think -> act -> observe -> decide` 主循环正确结束
- `tool`：工具注册、查找、执行、失败返回统一结果
- `middleware`：高危调用在执行前被拦截
- `architecture`：分层不能直接依赖具体工具实现

最小验收标准：

- 所有新增 public 行为都有单元测试
- 关键流程至少一条成功与一条失败路径
- 非 debug 与 debug 输出行为保持一致（尤其是 `OBSERVATIONS`）
