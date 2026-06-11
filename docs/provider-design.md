# Provider 设计

## 目标

Provider 是模型协议适配层，负责隔离不同大模型厂商的 API 差异。

当前项目的核心原则是：`Runtime` 只维护 Agent 主循环，`Provider` 只负责把模型输入输出翻译成项目内部协议。任何 OpenAI、Claude、DeepSeek 或其它模型协议差异，都不应泄漏到 `AgentEngine`。

当前 Tiny Agent Harness 以 `AgentContext` 作为 Provider 输入上下文，并把系统提示词的组装职责放在 Runtime 的 `context` 层。
System Prompt 由 `context` 层组装，Provider 只接收已经组装完成的字符串。

## 边界

Provider 负责：

- 根据 `AgentContext`、`DecisionPhase`、工具定义和外部传入的 System Prompt 请求模型。
- 把内部状态、上下文和工具信息翻译成厂商 API 请求。
- 把厂商响应翻译回内部 `Decision`。
- 处理厂商协议差异、请求失败和响应格式校验。

Provider 不负责：

- 推进主循环。
- 执行工具。
- 判断工具是否允许执行。
- 维护本轮运行上下文。
- 输出运行日志。
- 读取 `AGENTS.md` 或扫描 Skills。
- 拼接 Minimal Core、环境约束或阶段约束。

这些职责分别属于 `Runtime`、`Context`、`ToolRegistry`、`Tool`、`RunLogger`、`RunMetrics` 和 `TraceRecorder`。

## 当前接口基线

Java 版 Provider 以当前接口为准：

```java
Decision decide(AgentContext context, DecisionPhase phase, List<ToolDefinition> availableTools,
        String systemPrompt);
```

Provider 只能返回项目内部的 `Decision` 类型：

- `ThinkingDecision`：慢思考阶段的文本结果。
- `ToolDecision`：请求执行一个工具。
- `ParallelToolDecision`：请求执行多个工具。
- `FinishDecision`：任务完成并给出最终回答。

`Runtime` 不根据模型厂商写分支，也不直接依赖任何厂商 SDK 类型。

## 当前实现

当前已有两个 OpenAI-compatible Chat Completions Provider：

- `LmStudioModelProvider`：默认应用装配使用，读取 `lmstudio.baseUrl` 与 `lmstudio.model`。
- `SiliconFlowModelProvider`：读取 `siliconflow.apiKey`、`siliconflow.baseUrl` 与 `siliconflow.model`，请求时带 `Authorization: Bearer ...`。

共同特征：

- 使用 Java 21 `HttpClient` 调用 `<baseUrl>/chat/completions`。
- 请求固定为非流式 `stream=false`。
- `ACTION` 阶段挂载工具定义，允许模型输出工具调用或最终回答。
- 多个工具调用会映射为 `ParallelToolDecision`。
- Provider debug 输出只保留摘要和截断后的 JSON，避免把完整工具 schema 大段刷屏。
- 第一条 system message 使用调用方传入的 `systemPrompt`，Provider 不自行拼接项目规范。
- Provider 会读取响应中的 `usage`，并把 Token 信息交给 Runtime 指标层；缺失时按 unavailable 记录。

## 协议适配策略

实现时应把厂商消息结构收敛到项目内部模型：

- 模型普通文本响应收敛为 `FinishDecision` 或 `ThinkingDecision`。
- 模型工具调用响应收敛为 `ToolDecision` 或 `ParallelToolDecision`。
- 工具名、参数和调用 ID 收敛为内部 `ToolCall`。
- 厂商错误统一转换为 Provider 异常，由 `Runtime` 按 `provider_error` 失败规则处理。

`app` 层负责选择并装配具体 Provider；当前命令行和 Telegram Webhook 默认装配 LM Studio。

## 工具调用策略

`DecisionPhase` 决定模型是否能看到工具：

- `THINKING`：不挂载工具，只允许模型输出思考文本，并由 Provider 返回 `ThinkingDecision`。
- `ACTION`：挂载可用工具，允许模型输出工具调用或最终回答。

物理工具执行必须等完整工具调用参数生成并通过 Provider 解析后，才交给 `Runtime`、`ToolRegistry` 和 `Tool`。Provider 不允许边流式生成参数边触发工具执行。

## 验收标准

Provider 相关测试至少覆盖：

- `AgentEngine` 不出现厂商 SDK import。
- `provider` 层不直接执行工具。
- Provider 单元测试覆盖文本完成、工具调用、空响应、usage 解析和厂商错误。
- 架构测试能阻止 Runtime 依赖具体 Provider 实现或厂商 SDK 类型。
