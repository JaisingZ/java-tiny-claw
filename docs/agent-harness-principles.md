# Agent Harness 设计原则

本文档定义 `Tiny Agent Harness` 的项目级架构基线。后续所有设计与实现都以本文档为准；若实现与本文档冲突，先同步文档，再改代码。

## 1. 目标

- 构建一个可控、可测试、可观测的 Java AI Agent Harness。
- 让大模型负责“决策”，Harness 负责“运行时控制”。
- 用清晰边界替代黑盒框架，降低上下文失控和边界失控。
- 用独立 Context 层组装 System Prompt，避免 Provider 内部堆叠面条提示词。

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

当前真实物理工具集保持最小化：

- `read_file`
- `write_file`
- `edit_file`
- `bash`

### 3.3 上下文模型

当前 Tiny Agent Harness 精简版不做任务持久化/恢复。

- 运行时只维护内存上下文 `AgentContext`。
- 每次运行由 CLI 输入或通信入口消息创建新的 `Task`。
- 步进信息不跨 JVM 持久化。
- 历史上 `io.github.tinyclaw.agent.state`、`StateStore`、`checkpoint` 可作为外部恢复方向，但这些能力不在当前实现中。

### 3.4 安全边界

当前没有独立 `middleware` 包，也没有完整审批/授权层。当前安全边界由以下实现承担：

- `Tool` 自身校验参数和工作区路径边界。
- `ToolRegistry` 统一路由工具调用，并把未知工具和工具异常包装为失败结果。
- `AgentEngine` 按 `Tool.isSideEffect()` 处理只读并发和涉写串行。
- `RunLogger` 和 `RunResult` 保留人类可读的运行观测。

审批、白名单、黑名单、风险分级等治理能力属于后续扩展方向，不能写成当前已实现。

### 3.5 观测优先

- 以可读日志为主，使用 `RunLogger` 输出关键步骤。
- CLI 非 debug 场景输出 `OBSERVATIONS`，用于闭环判断。
- 最终输出以 `RunResult` 为准，覆盖成功/失败、步数与观察信息。

### 3.6 接口分层

- 通过接口隔离 Provider、Tool、Runtime 与通信入口。
- 实现可替换，边界不能模糊。
- 不把模型厂商能力泄漏到 Runtime，不把运行时控制放进 Provider。

## 4. 架构边界

### 4.1 Runtime

Runtime 负责主循环与控制流。

- 接收 `Task`
- 创建并推进 `AgentContext`
- 调用 `ModelProvider`
- 处理 `ThinkingDecision`、`FinishDecision`、`ToolDecision`、`ParallelToolDecision`
- 执行工具调用并记录观测
- 决定继续、成功或失败

Runtime 不负责具体工具实现，也不处理模型厂商协议。

### 4.2 Provider

Provider 负责模型协议适配。

- 把 `AgentContext` + `DecisionPhase` + 工具定义 + 已组装的 System Prompt 映射为厂商请求。
- 把厂商响应映射为项目内部 `Decision`。
- 处理厂商错误与响应校验。

Provider 不执行工具、不修改主循环状态。
Provider 不读取 `AGENTS.md`、不扫描 Skills、不在内部拼接运行时提示词。

### 4.3 Context

Context 负责上下文工程与 System Prompt 组装。

- `DefaultPromptComposer` 组装 Minimal Core、运行环境约束、阶段约束、工作区规范和 Skill 摘要。
- `AgentsFileLoader` 只读取标准 `AGENTS.md`；不兼容、不读取 legacy `AGENT.md`。
- `SkillLoader` 只扫描 `.tinyclaw/skills/**/SKILL.md` 的 `name` 和 `description` 摘要，不注入正文。
- Context 不执行工具、不请求模型、不推进主循环。

### 4.4 Tool Registry

Tool Registry 负责工具声明与路由执行。

- 注册工具。
- 按名查找工具。
- 暴露工具定义给模型。
- 路由执行并包装统一结果。

### 4.5 Communication

Communication 负责把外部消息转换成 Agent 任务。

- `communication` 定义统一聊天消息、会话输出和串行调度。
- `communication.telegram` 提供 Telegram Webhook 接入、Webhook 注册和消息回复。
- 通信层不把 Telegram 协议泄漏进 Runtime。

### 4.6 StateStore（历史目标）

- 历史目标：任务计划、当前步骤、历史摘要、错误信息可持久化，并可恢复。
- 当前 Tiny Agent Harness：不实现 `StateStore`，对应历史分层 `io.github.tinyclaw.agent.state` 已停用。

### 4.7 Tracer（历史目标）

- 历史目标：记录结构化 trace、决策链、模型输入输出。
- 当前 Tiny Agent Harness：不实现 `Tracer`，对应历史分层 `io.github.tinyclaw.agent.trace` 已停用；观测由 `RunLogger` + `RunResult` 覆盖。

## 5. 默认实现策略

### 5.1 当前形态

- 模块化单体。
- 以 CLI、本地工具任务和 Telegram Webhook 为主要验证入口。
- 默认 Provider 装配使用 LM Studio OpenAI-compatible 服务。

### 5.2 循环模型

当前主循环采用：

```text
optional thinking -> action decision -> tool/finish -> observe -> decide
```

其中：

- `thinking`：可选 `THINKING` 阶段，模型输出 `ThinkingDecision`。
- `action decision`：`ACTION` 阶段，模型输出最终回答或工具调用。
- `tool/finish`：Runtime 执行工具或结束任务。
- `observe`：Runtime 记录工具结果并更新 `AgentContext`。
- `decide`：Runtime 判断继续、失败或结束。

### 5.3 失败处理

- Provider 异常转换为 `provider_error: ...`。
- 未知工具返回 `Unknown tool: <name>`。
- 工具异常转换为 `tool_error: ...`。
- 工具返回失败时直接结束本轮任务。
- 不支持的决策返回 `unsupported_decision`。
- Thinking 阶段返回非 `ThinkingDecision` 时返回 `unsupported_thinking_decision`。
- 并行工具执行异常返回 `parallel_execution_failed: ...`。
- 超过最大步数返回 `max_steps_exceeded`。

## 6. 开发约束

- 新功能必须明确落在哪一层：Runtime、Provider、Tool Registry、Communication 或 app 装配。
- 新工具必须先经过 `ToolRegistry` 注册，并由工具自身保留参数和物理边界校验。
- 新逻辑不能把控制流散落在业务层。
- 若跨层边界变模糊，优先重构边界再加功能。

## 7. 版本控制原则

- 本文档是实现前约束；实现发生变化要同步本文档。
- 未经文档确认，不应进入大规模实现。

## 8. 当前结论

当前稳定实现顺序是：

1. Runtime
2. Provider
3. Tool Registry
4. Communication
5. 可观测增强（RunLogger/RunResult 继续补齐）
6. 可选治理层（审批、白名单、风险控制）

## 9. 技术选型

- JDK: `Java 21`
- 构建: `Maven`
- 测试: `JUnit 5`、`AssertJ`、`Mockito`
- 架构约束测试: `ArchUnit`
- 序列化: `Jackson`
- 日志: `SLF4J` + `Logback`

## 10. 代码结构基线

```text
src/main/java/io/github/tinyclaw/agent
├── app            命令行入口与应用装配
├── communication  可选通信适配，含 Telegram Webhook
├── context        System Prompt 组装、AGENTS.md 和 Skill 摘要加载
├── domain         纯数据模型
├── provider       模型适配
├── runtime        主循环、RunLogger、RunResult
└── tool           工具注册与执行
```

对应测试结构：

```text
src/test/java/io/github/tinyclaw/agent
├── app
├── architecture
├── communication
├── provider
├── runtime
└── tool
```

## 11. 单元测试基线

- `runtime`：主循环能处理成功、失败、Thinking、单工具和并行工具。
- `tool`：工具注册、查找、执行、失败返回统一结果。
- `communication`：消息接收、串行调度、Webhook 安全校验和回复行为。
- `provider`：文本完成、工具调用、空响应和厂商错误。
- `architecture`：Runtime 不依赖具体 Provider 实现或厂商 SDK 类型。

最小验收标准：

- 所有新增 public 行为都有单元测试。
- 关键流程至少一条成功与一条失败路径。
- 非 debug 与 debug 输出行为保持一致，尤其是 `OBSERVATIONS` 和 `RESULT`。
