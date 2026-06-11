# Telegram Webhook 工作原理

## 核心机制

Telegram Bot 有两种常见收消息方式：

- Long polling：服务端反复调用 `getUpdates` 拉取消息。
- Webhook：服务端调用 `setWebhook` 注册 HTTPS 回调地址，之后 Telegram Bot API 在收到用户消息后主动把 update 推送到该地址。

当前项目使用 Webhook。服务注册回调 URL、可选 `secret_token` 和 `allowed_updates`；用户给 Bot 发消息后，Telegram 把消息包装成 update，通过 HTTPS POST 推到服务。服务在接收 update 后尽快返回 HTTP 200，实际 Agent 执行交给工作区串行执行器处理。

## 当前实现映射

| 角色 | 实现 | 说明 |
| --- | --- | --- |
| `setWebhook` 注册 | `TelegramWebhookRegistrar` | 调用 Telegram Bot API `setWebhook`，设置 `url`、`allowed_updates=["message"]`、`secret_token`、`drop_pending_updates`、`max_connections`。 |
| HTTPS 入口 | `telegram.webhook.url` 或 `telegram.webhook.tunnel=trycloudflare` | 使用固定公网 URL，或通过 trycloudflare 生成临时公网 HTTPS URL。 |
| Webhook 接收 | `TelegramTransport` | 使用 JDK `HttpServer` 监听 `telegram.webhook.path`，接收 Telegram POST update。 |
| Secret token 校验 | `TelegramTransport` | 校验 `X-Telegram-Bot-Api-Secret-Token` 是否等于 `telegram.webhook.secret`。 |
| Update 解析 | `TelegramTransport` | 只处理文本消息，转换为 `ChatMessage(messageId, chatId, senderId, text)`；非文本 update 返回 200 并忽略。 |
| 工作区串行执行 | `WorkspaceSerialExecutor` | 单线程串行执行同一工作区的 Agent 任务，避免并发写工作区。 |
| Agent 调度 | `ChatAgentService` | 将消息转换为 `Task("chat-" + messageId, text)`，创建带聊天日志的 `AgentEngine` 并提交执行。 |
| Agent Runtime | `AgentEngine` | 推进 Main Loop，处理模型决策、工具调用和最终结果。 |
| LLM/工具系统 | `LmStudioModelProvider` + `ToolRegistry` | 默认宿主使用 LM Studio OpenAI-compatible Provider，并注册 `read_file`、`write_file`、`edit_file`、`bash`、`spawn_subagent`。 |
| 会话记录 | `SessionManager` + `WorkingMemoryPolicy` | 按消息来源隔离进程内 Session，每次请求模型前截取最近 Working Memory，默认 12 条消息、12000 字符。 |
| 任务级状态 | `DefaultPromptComposer` + Plan Mode | `agent.planMode=true` 时按 chat 生成 `.tinyclaw/state/chat/<chatId>/` 状态目录，引导模型维护 `PLAN.md` / `TODO.md`。 |
| 工具审批 | `tool.permission` + `communication.approval` | Telegram 模式可挂载审批 Middleware，支持 YAML 规则、热更新、`/approve <id>` 和 `/reject <id>`。 |
| Provider debug | `agent.debug` + SLF4J | 只把 Provider request / response / decision 摘要写入服务端日志，不发送到聊天窗口。 |
| 回复用户 | `TelegramSession` / `TelegramRunLogger` | 通过 Bot API `sendMessage` 回发状态、错误和最终回答。 |

## 当前消息流

```text
Telegram Bot API
  -> HTTPS 公网入口/trycloudflare
  -> TelegramTransport
  -> ChatMessage
  -> ChatAgentService
  -> WorkspaceSerialExecutor
  -> AgentEngine
  -> TelegramRunLogger / TelegramSession
  -> Telegram Bot API
  -> 用户
```

`TelegramTransport` 调用 `ChatAgentService.handle(...)` 后返回 HTTP 200。`ChatAgentService` 把实际 Agent 执行提交给 `WorkspaceSerialExecutor`，因此 Webhook 接收线程不直接运行完整 Main Loop。

## 配置入口

默认运行读取项目根目录 `agent.properties`；如果根目录不存在，则读取 classpath 中的 `agent.properties`。应用启动配置和 Webhook 宿主配置都使用 properties key。

常用 properties key：

- `telegram.bot.token`：Bot Token，必填。
- `telegram.webhook.url`：公网 HTTPS Webhook URL；为空且未启用 trycloudflare 时只启动 HTTP server，不注册公网 Webhook。
- `telegram.webhook.secret`：可选 secret token，用于校验 Telegram 请求头。
- `telegram.webhook.host`：监听地址，默认 `0.0.0.0`。
- `telegram.webhook.port`：监听端口，默认 `8080`。
- `telegram.webhook.path`：Webhook path，默认 `/telegram/webhook`。
- `telegram.webhook.tunnel`：设为 `trycloudflare` 时启动临时公网 HTTPS 隧道。
- `telegram.webhook.dropPendingUpdates`：注册 Webhook 时是否丢弃积压 update，默认 `false`。
- `telegram.webhook.maxConnections`：传给 `setWebhook.max_connections`，默认 `40`。
- `telegram.webhook.registrationDelaySeconds`：trycloudflare 动态 URL 注册前延迟秒数，默认 `60`。
- `telegram.webhook.registrationMaxAttempts`：`setWebhook` 最大尝试次数（含首次），默认 `3`。
- `telegram.webhook.registrationRetryIntervalSeconds`：`setWebhook` 重试间隔秒数，默认 `20`。
- `agent.workdir`：Webhook 模式 Agent 工作目录，默认 `.`。
- `agent.maxSteps`：Webhook 模式 Agent 最大步数，默认 `8`。
- `agent.enableThinking`：Webhook 模式是否开启 Thinking，默认 `false`。
- `agent.planMode`：Webhook 模式是否开启任务级状态外部化，默认 `false`。
- `agent.debug`：Webhook 模式是否把 Provider request / response / decision 摘要写入服务端 SLF4J 日志，默认 `false`；不发送到 Telegram 聊天窗口。
- `agent.workingMemory.maxMessages`：每个 Session 进入模型请求的最大消息数，默认 `12`。
- `agent.workingMemory.maxChars`：每个 Session 进入模型请求的最大字符数，默认 `12000`。
- `agent.permissions.enabled`：是否启用 Telegram 工具审批 Middleware，默认 `false`。
- `agent.permissions.file`：权限 YAML 文件路径，默认 `.tinyclaw/permissions.yaml`；相对路径按 `agent.workdir` 解析。
- `agent.permissions.hotReload`：是否监听权限 YAML 并热更新，默认 `true`。

Telegram 长驻入口按 `chatId`、`senderId`、`messageId` 的优先级选择会话标识。Session 只在进程内保存，进程重启后清空；Plan Mode 的 `PLAN.md` / `TODO.md` 是独立的任务级文件状态。
