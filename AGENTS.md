# 项目开发约定

## 基本要求

- 始终使用简体中文沟通。
- 简洁至上，遵守 KISS 原则。
- 涉及多个模块代码修改时，开启 Subagent，并使用 GPT-5.3-Codex-Spark 执行代码。
- 所有设计、实现、重构、排障，都必须以 [`docs/agent-harness-principles.md`](docs/agent-harness-principles.md) 为最高设计原则。

## 开发规则

- 先遵循设计文档，再写代码。
- 如果实现与设计文档冲突，先更新设计文档，再修改代码。
- 新功能必须明确属于哪一层：`Runtime`、`Provider`、`Context`、`Tool Registry`、`Communication`、`Middleware`、`app` 装配、可观测增强或可选治理层。
- 不引入与设计文档冲突的复杂框架、隐式状态机或臃肿工具集。
- 默认按以下顺序推进实现：
  1. `Runtime`
  2. `Provider`
  3. `Context`
  4. `Tool Registry`
  5. `Communication`
  6. `Middleware`
  7. `app` 装配
  8. 可观测增强
  9. 可选治理层

## 执行底线

- 所有高危操作必须可拦截、可审计、可回放。
- 长程任务状态必须通过明确机制外部化，例如 Plan Mode 的 `.tinyclaw/state/.../PLAN.md` 与 `TODO.md`。
- 所有新增逻辑都要保持边界清晰，避免把控制流散落到业务代码里。
