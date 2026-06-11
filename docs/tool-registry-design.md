# Tool Registry 设计

## 目标

Tool Registry 是工具注册与分发层，负责把模型产生的工具调用意图路由到具体 `Tool` 实现。

它的核心目标是让 `Runtime` 不感知具体工具细节：

- 模型只看到工具定义。
- `Runtime` 只看到 `ToolCall` 和 `ToolResult`。
- 具体工具只关心自己的参数校验和物理执行。

## 边界

Tool Registry 负责：

- 注册工具实例。
- 按工具名查找工具。
- 暴露工具定义列表给 Provider。
- 路由执行工具调用。
- 在工具执行前运行已挂载的 Middleware。
- 把未知工具和工具异常包装成 `ToolResult.failure(...)`。

Tool Registry 不负责：

- 推进主循环。
- 适配模型厂商协议。
- 自行维护完整审批、白名单、黑名单或风险分级。

这些职责分别属于 `Runtime`、`Provider`、`tool.permission` 和 `communication.approval`。`ToolRegistry` 只提供 Middleware 挂载点。

## 当前接口基线

工具必须实现 `Tool` 接口，并明确其是否具有副作用：

```java
String name();

ToolDefinition definition();

ToolResult execute(ToolCall call, AgentContext context);

default boolean isSideEffect() {
    return true;
}
```

`ToolRegistry` 当前提供四类能力：

```java
ToolRegistry register(Tool tool);

ToolRegistry use(ToolMiddleware middleware);

Tool require(String toolName);

Map<String, Tool> snapshot();

List<ToolDefinition> definitions();

ToolResult execute(ToolCall call, AgentContext context);
```

`register` 负责挂载工具，`use` 负责挂载执行前中间件，`definitions` 负责向模型暴露工具 Schema，`snapshot` 用于主循环判断工具副作用属性，`execute` 是统一执行入口。

Middleware 签名如下：

```java
ToolResult execute(ToolCall call, AgentContext context, ToolExecution next);
```

Middleware 在工具存在后、底层 `Tool.execute(...)` 前执行。中间件可直接返回失败结果阻断底层工具，也可调用 `next.execute(...)` 放行。

未知工具不会抛出到主循环外，而是返回：

```text
ToolResult.failure("Unknown tool: <name>")
```

工具实现抛出的运行时异常会被包装为：

```text
ToolResult.failure("tool_error: <message>")
```

Middleware 抛出的运行时异常会被包装为：

```text
ToolResult.failure("middleware_error: <message>")
```

## 执行流程

`AgentEngine` 处理工具决策时遵循“只读并发、涉写串行”原则：

1. **单工具决策**：`ToolDecision` 直接通过 `ToolRegistry.execute(call, context)` 执行。
2. **多工具决策**：`ParallelToolDecision` 承载多个 `ToolCall`。
3. **只读并发**：`AgentEngine` 通过 `ToolRegistry.snapshot()` 找到工具实例，`tool.isSideEffect()==false` 的工具会并发执行。
4. **涉写串行**：写工具或未知副作用工具按模型返回顺序串行执行。
5. **Middleware 检查**：每个工具调用在真正执行前先通过 Registry 中的 Middleware 链。
6. **结果聚合**：所有工具结果成功后，输出按原始调用顺序合并写入 `AgentContext` observation。

并发组中任一工具失败都会让本轮任务失败；并发执行异常会返回 `parallel_execution_failed: ...`。

## 决策模型

为了支持多工具并行，`Decision` 接口包含以下实现：

- `ThinkingDecision`：可选 `THINKING` 阶段的思考文本。
- `FinishDecision`：最终回答。
- `ToolDecision`：单个工具调用。
- `ParallelToolDecision`：多个工具调用。

`ToolDecision` 是单工具特例；`ParallelToolDecision` 用于读多文件等互相独立操作。

## 基础工具

当前真实物理工具集保持极简，只包含文件读写、局部编辑和命令执行。

### read_file

- **属性**：`isSideEffect() -> false`，只读，支持并发。
- **参数**：`path`。
- **职责**：读取工作区内文件。

约束：

- `path` 不能为空。
- 规范化后的目标路径必须仍在工作目录内。
- 文件不存在返回失败结果。
- 读取失败返回失败结果。
- 输出超过 `8_000` 字符时截断，并在输出末尾追加截断提示。

### write_file

- **属性**：`isSideEffect() -> true`，写操作，强制串行。
- **参数**：`path`、`content`。
- **职责**：在工作区内创建或覆盖 UTF-8 文本文件。

约束：

- `path` 不能为空。
- `content` 必须存在，允许为空字符串。
- 内容不能包含 NUL 字节。
- 规范化后的目标路径必须仍在工作目录内。
- 父目录不存在时自动创建。
- 目录创建或写入失败返回失败结果。

### edit_file

- **属性**：`isSideEffect() -> true`，写操作，强制串行。
- **参数**：`path`、`old_text`、`new_text`。
- **职责**：对工作区内已有文件做一次局部文本替换。

约束：

- `path` 不能为空。
- `old_text` 不能为空。
- `new_text` 必须存在，允许为空字符串以删除片段。
- 规范化后的目标路径必须仍在工作目录内。
- 文件不存在、读取失败或写入失败返回失败结果。
- 匹配链按精确匹配、换行符归一化、代码块首尾裁剪、逐行去首尾空格匹配逐级降级。
- 匹配不到或匹配到多处都返回失败结果；多处匹配时要求模型提供更多上下文，不能猜测替换位置。

### bash

- **属性**：`isSideEffect() -> true`，默认保守策略，强制串行。
- **参数**：`command`。
- **职责**：在工作区内执行命令，返回合并后的 stdout 和 stderr。

约束：

- `command` 不能为空。
- 按运行平台选择可用命令解释器执行。
- 命令默认 30 秒超时，超时后终止进程并返回提示。
- 非 0 退出码不作为工具失败，而是返回 `exitCode=<code>` 和输出，让模型自纠错。
- 空输出返回明确成功消息。
- 输出超过 `8_000` 字符时截断，并在输出末尾追加截断提示。

## 安全策略

Tool Registry 是分发层，不是完整安全策略层。当前安全策略按最小实现分布在工具自身和运行时：

- `Tool`：校验自身参数和物理边界，例如路径不能逃逸工作区。
- `ToolRegistry`：统一路由、Middleware 链、未知工具处理和异常包装。
- `tool.permission`：按 `.tinyclaw/permissions.yaml` 的不可变快照计算 `allow / ask / deny`，支持工具名和参数正则匹配，冲突时 `deny > ask > allow`。
- `communication.approval`：在聊天入口实现人工审批等待、放行、拒绝和超时清理。
- `AgentEngine`：基于 `Tool.isSideEffect()` 限制写操作串行执行。
- `RunLogger`：记录工具执行关键日志，运行结论输出为 `RunResult`。

Telegram Webhook 模式可选择启用审批 Middleware；CLI `run` 默认不启用，保持命令行运行语义。权限规则默认从工作目录下 `.tinyclaw/permissions.yaml` 读取，文件不存在时使用禁用快照并全部放行。热更新成功后只影响新的工具调用；解析失败时保留 last-known-good 快照。`agent.permissions.tool.*` 和 `agent.permissions.denyPattern.*` properties 可作为无 YAML 文件时的 properties 配置来源。

示例：

```yaml
version: 1
enabled: true
defaultAction: ask
approvalTimeoutSeconds: 1800

rules:
  - id: allow-read
    tools: [read_file]
    action: allow

  - id: deny-dangerous-bash
    tools: [bash]
    action: deny
    arguments:
      command:
        regex: "(?i)\\b(rm\\s+-rf|sudo\\b|drop\\s+(database|table)|kubectl\\s+delete)\\b"

  - id: ask-write-tools
    tools: [write_file, edit_file, bash]
    action: ask
```

## 测试要求

新增或修改工具能力时至少覆盖：

- 工具注册后能暴露稳定 `ToolDefinition`。
- 已注册工具能通过 Registry 执行。
- 未知工具返回失败结果。
- 工具异常返回失败结果。
- Middleware 放行、阻断、执行顺序和异常包装。
- 动态权限 YAML 解析、优先级、非法配置、热更新和 last-known-good 行为。
- 具体工具的参数校验、成功路径和失败路径。
- `AgentEngine` 能正确处理工具成功与失败。

当前对应测试：

- `ToolRegistryTest`
- `ReadFileToolTest`
- `WriteFileToolTest`
- `EditFileToolTest`
- `BashToolTest`
- `AgentEngineTest`
- `MainLoopJavaPrimitiveSmokeTest`
