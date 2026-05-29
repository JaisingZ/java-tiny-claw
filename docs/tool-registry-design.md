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

工具必须实现 `Tool` 接口，并明确其是否具有副作用：

```java
String name();

ToolDefinition definition();

ToolResult execute(ToolCall call, AgentState state);

/**
 * 标识工具是否具有副作用（写操作）。
 * 默认为 true（保守策略），只读工具（如读取文件）应重写为 false。
 */
default boolean isSideEffect() {
    return true;
}
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

AgentEngine 处理工具决策时遵循“只读并发、涉写串行”原则：

1. **分类分组**：根据 `tool.isSideEffect()` 将单轮 `tool_calls` 分为“只读组”和“涉写组”。
2. **并发执行（只读组）**：
   - 使用线程池并行调用 `ToolRegistry.execute(call, state)`。
   - 所有并发任务共享当前步骤的 `AgentState` 快照。
3. **顺序执行（涉写组）**：
   - 在只读组全部完成后，按顺序串行执行涉写工具。
   - 每个写操作均在主线程中按模型返回顺序执行，确保状态一致性。
4. **结果聚合**：汇总所有工具结果，按原始 ID 匹配后返回给模型。

未知工具不会抛出到主循环外，而是返回：

```text
ToolResult.failure("Unknown tool: <name>")
```

工具实现抛出的运行时异常会被包装为：

```text
ToolResult.failure("tool_error: <message>")
```

这样主循环只需要处理统一的成功或失败结果。

## 决策模型升级

为了支持多工具并行，`Decision` 接口新增 `ParallelToolDecision` 实现：

- **ParallelToolDecision**：承载 `List<ToolCall>`，指示 `AgentEngine` 启动并行/串行混合执行流程。
- **单工具回退**：原有的 `ToolDecision` 视为仅包含一个调用的特例。

## 基础工具

当前第一版真实物理工具集保持极简，只包含文件读写、局部编辑和命令执行。

### read_file

- **属性**：`isSideEffect() -> false` (只读，支持并发)

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

### write_file

- **属性**：`isSideEffect() -> true` (写操作，强制串行)

职责：

- 在工作区内创建或覆盖文件。
- 参数只接受相对工作区路径和完整文件内容。
- 父目录不存在时自动创建。

约束：

- `path` 不能为空。
- `content` 必须存在，允许为空字符串。
- 规范化后的目标路径必须仍在工作目录内。
- 目录创建或写入失败返回失败结果。

### edit_file

- **属性**：`isSideEffect() -> true` (写操作，强制串行)

职责：

- 对工作区内已有文件做一次局部文本替换。
- 参数只接受相对工作区路径、旧文本和新文本。
- 优先用于修改长文件中的小片段，避免让模型重写整个文件。

约束：

- `path` 不能为空。
- `old_text` 不能为空。
- `new_text` 必须存在，允许为空字符串以删除片段。
- 规范化后的目标路径必须仍在工作目录内。
- 文件不存在、读取失败或写入失败返回失败结果。
- 匹配链按精确匹配、换行符归一化、代码块首尾裁剪、逐行去首尾空格匹配逐级降级。
- 匹配不到或匹配到多处都返回失败结果；多处匹配时要求模型提供更多上下文，不能猜测替换位置。

### bash

- **属性**：`isSideEffect() -> true` (默认保守策略，强制串行，因为脚本内容不可知)

职责：

- 在工作区内执行命令。
- Windows 使用 PowerShell，非 Windows 使用 Bash。
- 返回合并后的 stdout 和 stderr。

约束：

- `command` 不能为空。
- 命令默认 30 秒超时，超时后终止进程并返回警告。
- 非 0 退出码不作为工具失败，而是返回退出码和输出，让模型自纠错。
- 空输出返回明确成功消息。
- 输出超过固定阈值时截断，并在输出末尾追加截断提示。

当前不实现：

- 后台进程管理。
- 动态工具发现。
- MCP 或插件加载。

## 安全策略

Tool Registry 是分发层，不是完整安全策略层。`bash` 按本地开发 YOLO 思路实现，不内置黑名单。

执行前的授权、白名单、黑名单、审批和风险分级应放在 `Middleware`。由于引入了并行执行，需遵循以下规范：

- **线程安全**：所有注册到 Registry 的 `Middleware`（如权限校验、日志记录）必须实现为线程安全。
- **资源保护**：Registry 或 Engine 应为并行执行设置最大线程数限制，防止模型产生的过量调用耗尽系统资源。
- **异常隔离**：并发组中某个工具的失败不应直接导致主线程崩溃，应收集错误信息并继续处理后续决策。

具体工具仍必须保留自己的底线防御，例如 `read_file` 的路径边界检查。

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
- `WriteFileToolTest`
- `EditFileToolTest`
- `BashToolTest`
- `AgentEngineTest`

## 演进方向

后续只在确有需求时扩展：

- 为 `ToolResult` 增加 `errorCode`、`retryable`、`riskLevel`。
- 为大输出增加 offloading：完整结果写入临时文件，模型只收到首尾预览和引用路径。
- 为重复失败增加重试预算和熔断。
- 为 `edit_file` 增加更精细的缩进保留策略。

不要在当前阶段引入动态类加载、复杂插件系统或外部协议绑定。Registry 应保持小而稳定。
