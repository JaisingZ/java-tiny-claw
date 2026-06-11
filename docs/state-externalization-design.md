# State Externalization 设计

## 目标

`State Externalization` 用于长程任务的断点续跑和人机协同。

当前版本只在 `Plan Mode` 开启时，通过 `PromptComposer` 注入规则，引导模型把任务级状态写入隐藏目录中的 Markdown 文件：

- `PLAN.md`：记录目标、约束、方案和关键决策。
- `TODO.md`：记录细颗粒度 checklist 和当前进度。

## 状态目录

状态目录始终位于工作区内的 `.tinyclaw/state/`，避免直接污染项目根目录。

```text
.tinyclaw/
  state/
    cli/
      default/
        PLAN.md
        TODO.md
    chat/
      <chatId>/
        PLAN.md
        TODO.md
```

CLI `run --plan` 使用 `.tinyclaw/state/cli/default/`。Telegram 模式开启 `agent.planMode=true` 后，按 `chatId` 使用 `.tinyclaw/state/chat/<chatId>/`。

## 数据流

```text
AgentApplication / TelegramAgentWebhookService
  -> DefaultPromptComposer(workDir, planMode, stateDir)
  -> AgentEngine
  -> ModelProvider
  -> read_file / write_file / edit_file
  -> .tinyclaw/state/.../PLAN.md, TODO.md
```

`AgentEngine` 不解析 `PLAN.md` 或 `TODO.md`，也不判断 checklist 状态。Runtime 只负责把 `planMode` 和 `stateDir` 放进 System Prompt；状态文件的读取、创建和更新仍由模型通过现有工具完成。

## Prompt 规则

Plan Mode 开启后，ACTION 阶段需要遵守：

- 长程任务开始时先检查状态目录下的 `PLAN.md` 和 `TODO.md`。
- 文件不存在时，先创建 `PLAN.md`，再创建 `TODO.md`。
- 文件存在时，先读取两份文件，再按已有进度续跑。
- 每完成明确步骤后，更新 `TODO.md` 的 checkbox。
- 最终回答说明实际进展和剩余事项，不能只说状态已更新。
- 简单问答或短命令不需要创建状态文件。

Plan Mode 关闭时不注入这些规则，保持当前轻量运行面。

## 与现有会话机制的关系

- `AgentSession`：短期会话记录，进程内保存，进程重启后丢失。
- `WorkingMemoryPolicy`：控制哪些会话消息进入当前请求。
- `ContextCompactor`：控制 Provider 请求前的超长 observation。
- `PLAN.md` / `TODO.md`：任务级状态记忆，跨进程保留，可由人直接编辑。

这四者职责不同。状态外部化不替代 Working Memory，也不改变 Context Compaction。

## 验收

关键测试覆盖：

- 默认 Prompt 不包含 `PLAN.md` / `TODO.md` 强制规则。
- Plan Mode Prompt 包含状态目录、启动嗅探、创建/读取/更新规则。
- CLI 能解析 `run --plan --prompt ...`。
- `agent.planMode` 能从配置读取。
- 通信层可按不同 `chatId` 生成不同状态目录。
