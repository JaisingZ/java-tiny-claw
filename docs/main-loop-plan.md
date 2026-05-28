# Main Loop 规划

## 目标

把 Agent 的最小闭环收敛为一个明确、可测试、可审计的主循环。

主循环只做四件事：

1. 读取状态
2. 请求模型决策
3. 执行工具或结束任务
4. 保存状态并记录 trace

## 最小流程

```text
load state
if status != RUNNING: return

while status == RUNNING && stepCount < maxSteps:
  request model decision
  if FinishDecision:
    finish and return
  if ToolDecision:
    middleware check
    resolve tool
    execute tool
    if success:
      advance + observe
      continue
    fail and return
  fail unsupported decision and return

if still RUNNING:
  fail max_steps_exceeded
```

## 约束

- `ModelProvider` 只负责给出 `Decision`
- `ToolRegistry` 只负责暴露工具定义、查找工具、路由执行工具
- `ToolMiddleware` 只负责执行前拦截
- `StateStore` 只负责状态持久化
- `TraceRecorder` 只负责过程记录

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
- 每轮关键步骤都有 trace
- `maxSteps` 能截断无限循环
