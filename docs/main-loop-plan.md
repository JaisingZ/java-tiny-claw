# Main Loop 规划

## 目标

把 Agent 的最小闭环收敛为一个明确、可测试、可观测的主循环。

当前 tiny-claw 的实现是“精简版”：主循环只做四件事：

1. 初始化/更新运行时上下文 `AgentContext`
2. 请求模型决策
3. 执行工具或结束任务
4. 落日志并返回 `RunResult`

历史上 `StateStore` 与 `TraceRecorder` 是可选扩展，但当前版本**不实现状态持久化和结构化 trace 层**。

## 最小流程

```text
ctx = buildInitialContext(task)

while stepCount < maxSteps:
  if ctx.shouldStop(): return failure(ctx)
  request model decision
  if FinishDecision:
    finish and return ctx.toRunResult()
  if ToolDecision:
    middleware check
    resolve tool from registry
    execute tool
    if success:
      observe and enrich ctx
      continue
    fail and return
  else:
    fail unsupported decision and return

if limit reached:
  fail max_steps_exceeded
```

## 约束

- `ModelProvider` 只负责给出 `Decision`
- `ToolRegistry` 只负责暴露工具定义、查找工具、路由执行工具
- `ToolMiddleware` 只负责执行前拦截
- `AgentContext` 只负责本轮运行时上下文（内存）
- `RunLogger` 负责可读日志输出（debug/非 debug 模式分别输出）

## 失败规则

- provider 抛异常 -> `provider_error`
- middleware 拒绝 -> 直接失败
- 工具不存在 -> 失败
- 工具执行抛异常 -> `tool_error`
- 工具返回失败 -> 直接失败
- 超过最大步数 -> `max_steps_exceeded`

## 当前实现对应

- 主循环：`src/main/java/com/jaising/agent/runtime/AgentEngine.java`
- 成功路径测试：`src/test/java/com/jaising/agent/runtime/AgentEngineTest.java`
- 架构基线：`docs/agent-harness-principles.md`

## 验收标准

- 能从 `Task` 跑到 `SUCCESS` 或 `FAILED`
- 至少覆盖一条成功路径和三条失败路径
- 每轮关键步骤都有 `RunLogger` 可读日志
- `maxSteps` 能截断无限循环
