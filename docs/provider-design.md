# Provider 设计

## 目标

Provider 是模型协议适配层，负责隔离不同大模型厂商的 API 差异。

当前项目的核心原则是：`Runtime` 只维护 Agent 主循环，`Provider` 只负责把模型输入输出翻译成项目内部协议。任何 OpenAI、Claude、DeepSeek 或其它模型协议差异，都不应泄漏到 `AgentEngine`。

当前 tiny-claw 精简版将历史接口参数从 `AgentState` 迁移到 `AgentContext`，不再做持久化状态或 trace 组装。

## 边界

Provider 负责：

- 根据 `AgentContext` 和 `DecisionPhase` 请求模型。
- 把内部状态、上下文和工具信息翻译成厂商 API 请求。
- 把厂商响应翻译回内部 `Decision`。
- 处理厂商协议差异、请求失败和响应格式校验。

Provider 不负责：

- 推进主循环。
- 执行工具。
- 判断工具是否允许执行。
- 维护本轮运行上下文（仅内存）。
- 输出运行日志（由 `RunLogger` 负责）

这些职责分别属于 `Runtime`、`Tool Registry`、`Middleware`，以及 `RunLogger`；历史中的 `StateStore` 和 `TraceRecorder` 在当前 tiny-claw 版本不实现。

## 当前接口基线

Java 版 Provider 以当前接口为准：

```java
Decision decide(AgentContext context, DecisionPhase phase);
```

Provider 只能返回项目内部的 `Decision` 类型：

- `ThinkingDecision`：慢思考阶段的文本结果。
- `ToolDecision`：请求执行一个工具。
- `FinishDecision`：任务完成并给出最终回答。

`Runtime` 不根据模型厂商写分支，也不直接依赖任何厂商 SDK 类型。

## 协议适配策略

当前默认真实 Provider 实现 LM Studio OpenAI-compatible 协议，配置由 `properties` 文件读取，并由 `app` 层装配注入：

- `baseUrl`
- `model`

实现时应把厂商消息结构收敛到项目内部模型：

- 模型普通文本响应收敛为 `FinishDecision` 或 `ThinkingDecision`。
- 模型工具调用响应收敛为 `ToolDecision`。
- 工具名、参数和调用 ID 收敛为内部 `ToolCall`。
- 厂商错误统一转换为 Provider 异常，由 `Runtime` 按 `provider_error` 失败规则处理。

当前实现：

- `LmStudioConfig` 负责读取 `agent.properties` 或 `-Dagent.config=...` 指定的配置文件。
- `LmStudioModelProvider` 使用 Java 21 `HttpClient` 调用 `/chat/completions`。
- 请求固定为非流式 `stream=false`，流式响应后续单独设计。
- 当前 `app` 层只默认装配 LM Studio，不支持运行时切换不同 Provider。

## 工具调用策略

`DecisionPhase` 决定模型是否能看到工具：

- `THINKING`：不挂载工具，只允许模型输出思考文本，并由 Provider 返回 `ThinkingDecision`。
- `ACTION`：挂载可用工具，允许模型输出工具调用或最终回答。

物理工具执行必须等完整工具调用参数生成并通过 Provider 解析后，才交给 `Runtime`、`Middleware` 和 `Tool Registry`。Provider 不允许边流式生成参数边触发工具执行。

## 扩展策略

不同协议使用独立 Provider 实现：

- `OpenAiCompatibleProvider`
- `ClaudeProvider`
- `DeepSeekProvider`

新增 Provider 时只允许扩展 `provider` 层和 `app` 层装配代码，不允许在 `Runtime` 中增加厂商判断。

如果某个模型存在特殊协议要求，例如必须回传 reasoning 字段、工具参数分片格式不同、系统提示位置不同，应在对应 Provider 内处理。

## 验收标准

后续实现真实 Provider 时至少满足：

- `AgentEngine` 不出现厂商 SDK import。
- `provider` 层不直接执行工具。
- Provider 单元测试覆盖文本完成、工具调用、空响应和厂商错误。
- 架构测试能阻止 Runtime 依赖具体 Provider 实现或厂商 SDK 类型。
