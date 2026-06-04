# Main Loop 规划

## 目标

把 Agent 的最小闭环收敛为一个明确、可测试、可观测的主循环。

当前 Tiny Agent Harness 的实现是精简版：主循环只做四件事：

1. 初始化/更新运行时上下文 `AgentContext`
2. 请求模型决策
3. 执行工具或结束任务
4. 落日志并返回 `RunResult`

历史上 `StateStore` 与 `TraceRecorder` 是可选扩展，但当前版本不实现状态持久化和结构化 trace 层。

## 最小流程

```text
ctx = AgentContext.create(task)

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
      continue
    return failed(error)

  if ParallelToolDecision:
    execute read-only tools concurrently
    execute side-effect tools serially in model order
    if all success:
      ctx = ctx.advance().observe(joined_outputs)
      continue
    return failed(error)

  return failed("unsupported_decision")

return failed("max_steps_exceeded")
```

## 约束

- `ModelProvider` 只负责给出 `Decision`。
- `ToolRegistry` 只负责暴露工具定义、查找工具、路由执行工具。
- `Tool` 负责自身参数校验和物理边界校验。
- `AgentContext` 只负责本轮运行时上下文（内存）。
- `RunLogger` 负责可读日志输出。
- 当前没有独立的执行前拦截包。

## 失败规则

- Provider 抛异常 -> `provider_error: <message>`
- Thinking 阶段返回非 `ThinkingDecision` -> `unsupported_thinking_decision`
- 未知工具 -> `Unknown tool: <name>`
- 工具执行抛异常 -> `tool_error: <message>`
- 工具返回失败 -> 直接失败
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
- `maxSteps` 能截断无限循环。
- 工具失败不会穿透成主循环崩溃。
