# Main Loop 规划

## 目标

把 Agent 的最小闭环收敛为一个明确、可测试、可观测的主循环。

当前 Tiny Agent Harness 的主循环只做四件事：

1. 初始化/更新运行时上下文 `AgentContext`
2. 请求模型决策
3. 执行工具或结束任务
4. 落日志、指标、trace 并返回 `RunResult`

长程任务通过可选 Plan Mode 引导模型维护 `.tinyclaw/state/.../PLAN.md` 与 `TODO.md`。结构化回放由 `TraceRecorder` 输出本地 JSON trace。

## 最小流程

```text
ctx = AgentContext.create(task)
reminder = new SystemReminderInjector()

while ctx.stepCount < maxSteps:
  if enableThinking:
    request THINKING decision with no tools
    if ThinkingDecision:
      ctx = ctx.think(thought)
    else:
      return failed("unsupported_thinking_decision")

  request ACTION decision with tool definitions

  if FinishDecision:
    return success(answer)

  if ToolDecision:
    execute one tool through ToolRegistry
    if success:
      ctx = ctx.advance().observe(output)
    else:
      ctx = ctx.advance().observe(recovery_observation)
    if reminder is triggered:
      append [SYSTEM REMINDER] at the end of the current observation
    continue

  if ParallelToolDecision:
    execute read-only tools concurrently
    execute side-effect tools serially in model order
    convert each failed result to a recovery observation
    append at most one [SYSTEM REMINDER] for this turn
    ctx = ctx.advance().observe(joined_outputs)
    continue

  return failed("unsupported_decision")

return failed("max_steps_exceeded")
```

## 约束

- `ModelProvider` 只负责给出 `Decision`。
- `ToolRegistry` 只负责暴露工具定义、查找工具、路由执行工具。
- `Tool` 负责自身参数校验和物理边界校验。
- `AgentContext` 只负责本轮运行时上下文（内存）。
- `RunLogger` 负责可读日志输出。
- `RunMetrics` 负责模型和工具调用的汇总指标。
- `TraceRecorder` 负责结构化回放，默认写入 `.tinyclaw/traces/trace-<traceId>.json`。
- `SystemReminderInjector` 是单次 run 内的局部防呆状态，不跨任务复用。
- 执行前拦截通过 `ToolRegistry` Middleware 接入；当前 Telegram 模式可挂载工具审批，CLI `run` 默认不挂载审批中间件。

## 失败规则

- Provider 抛异常 -> `provider_error: <message>`
- Thinking 阶段返回非 `ThinkingDecision` -> `unsupported_thinking_decision`
- 未知工具 -> 写入 `Error executing <name>: Unknown tool: <name>` 观测
- 工具执行抛异常 -> `tool_error: <message>`
- 工具返回失败 -> 写入 `Error executing <tool>: <message>` 观测，命中规则时追加 `[Recovery Hint]`
- 连续无效工具调用 -> 在最新观测末尾追加 `[SYSTEM REMINDER]`，提醒模型停止重复、换策略或说明需要人工输入
- 并行工具执行异常 -> `parallel_execution_failed: <message>`
- 不支持的决策类型 -> `unsupported_decision`
- 超过最大步数 -> `max_steps_exceeded`

## 当前实现对应

- 主循环：`src/main/java/io/github/tinyclaw/agent/runtime/AgentEngine.java`
- 成功路径和失败路径测试：`src/test/java/io/github/tinyclaw/agent/runtime/AgentEngineTest.java`
- 集成测试：`src/test/java/io/github/tinyclaw/agent/runtime/AgentEngineIntegrationTest.java`
- Main Loop smoke：`src/test/java/io/github/tinyclaw/agent/runtime/MainLoopJavaPrimitiveSmokeTest.java`
- 架构基线：`docs/agent-harness-principles.md`

## 验收标准

- 能从 `Task` 跑到 `SUCCESS` 或 `FAILED`。
- 覆盖 `FinishDecision`、`ToolDecision`、`ParallelToolDecision` 和可选 `ThinkingDecision`。
- 每轮关键步骤都有 `RunLogger` 可读日志。
- 每次 CLI/Telegram 运行都能生成 Root/Turn/LLM/Tool 层级的本地 JSON trace。
- `maxSteps` 能截断无限循环。
- 工具失败不会穿透成主循环崩溃。
