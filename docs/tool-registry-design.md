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
- 把未知工具和工具异常包装成 `ToolResult.failure(...)`。

Tool Registry 不负责：

- 决定是否允许执行工具。
- 推进主循环。
- 保存状态。
- 记录 trace。
- 适配模型厂商协议。

这些职责分别属于 `Middleware`、`Runtime`、`StateStore`、`Tracer` 和 `Provider`。

## 当前接口基线

工具必须实现 `Tool` 接口：

```java
String name();

ToolDefinition definition();

ToolResult execute(ToolCall call, AgentState state);
```

`ToolRegistry` 当前提供四类能力：

```java
ToolRegistry register(Tool tool);

Tool require(String toolName);

List<ToolDefinition> definitions();

ToolResult execute(ToolCall call, AgentState state);
```

`register` 负责挂载工具，`definitions` 负责向模型暴露工具 Schema，`execute` 是统一执行入口。

## 执行流程

```text
AgentEngine
  -> Middleware.beforeTool(...)
  -> ToolRegistry.execute(call, state)
       -> require(call.toolName())
       -> tool.execute(call, state)
       -> ToolResult.success(...) 或 ToolResult.failure(...)
  -> AgentEngine 记录 TOOL_RESULT trace
  -> 成功则 observe，失败则 fail
```

未知工具不会抛出到主循环外，而是返回：

```text
ToolResult.failure("Unknown tool: <name>")
```

工具实现抛出的运行时异常会被包装为：

```text
ToolResult.failure("tool_error: <message>")
```

这样主循环只需要处理统一的成功或失败结果。

## read_file 工具

当前第一版真实物理工具是 `read_file`。

职责：

- 读取工作区内文件。
- 参数只接受相对工作区路径。
- 返回文件文本内容。

约束：

- `path` 不能为空。
- 规范化后的目标路径必须仍在工作目录内。
- 文件不存在返回失败结果。
- 读取失败返回失败结果。
- 输出超过固定阈值时截断，并在输出末尾追加截断提示。

当前不实现：

- 写文件。
- 编辑文件。
- 执行命令。
- 动态工具发现。
- MCP 或插件加载。

## 安全策略

Tool Registry 是分发层，不是完整安全策略层。

执行前的授权、白名单、黑名单、审批和风险分级应放在 `Middleware`。具体工具仍必须保留自己的底线防御，例如 `read_file` 的路径边界检查。

当前安全分层：

- `Middleware`：决定工具是否允许执行。
- `ToolRegistry`：统一路由和异常包装。
- `Tool`：校验自身参数和物理边界。
- `Tracer`：记录工具调用和结果。

## 测试要求

新增或修改工具能力时至少覆盖：

- 工具注册后能暴露稳定 `ToolDefinition`。
- 已注册工具能通过 Registry 执行。
- 未知工具返回失败结果。
- 工具异常返回失败结果。
- 具体工具的参数校验、成功路径和失败路径。
- `AgentEngine` 能正确处理工具成功与失败。

当前对应测试：

- `ToolRegistryTest`
- `ReadFileToolTest`
- `AgentEngineTest`

## 演进方向

后续只在确有需求时扩展：

- 为 `ToolResult` 增加 `errorCode`、`retryable`、`riskLevel`。
- 为大输出增加 offloading：完整结果写入临时文件，模型只收到首尾预览和引用路径。
- 为重复失败增加重试预算和熔断。
- 增加 `write`、`edit`、`bash` 等极简基础工具。

不要在当前阶段引入动态类加载、复杂插件系统或外部协议绑定。Registry 应保持小而稳定。
