# 通信服务抽象与 Telegram Webhook 接入设计

## 背景

当前 Main Loop 已收敛为 `AgentEngine`：它使用短期 `AgentContext` 推进任务，返回 `RunResult`，并通过 `RunLogger` 暴露面向人的运行过程。通信服务只负责把外部消息转成 `Task`，不把 Telegram 逻辑塞进 Runtime。

文章中的 Reporter 反转，在本项目中对应已有的 `RunLogger`：终端使用 `ConsoleRunLogger`，聊天平台使用平台专用 `RunLogger`。

## 架构边界

- `runtime` 不感知 Telegram，只接收 `Task` 并推进 Main Loop。
- `communication` 负责统一消息模型、会话输出、消息处理和工作区串行调度。
- `communication.approval` 负责 Telegram 模式下的人工审批等待、放行、拒绝和超时清理。
- `communication.telegram` 负责 Telegram Webhook 接收、`setWebhook` 注册、trycloudflare 隧道和消息发送。
- `telegram` 子命令用于显式启动 Webhook；无参数启动不再承载通信服务或 harness 信息输出。

## CLI 启动行为

- 无参数启动：缺少正式命令，提示使用 `run` 或 `telegram`。
- `telegram` 子命令：直接启动 `TelegramAgentWebhookService`，启动后阻塞等待关闭，退出时停掉服务。
- `run`：只走命令行运行，不启动 Telegram Webhook。

## 核心抽象

`io.github.tinyclaw.agent.communication` 提供以下接口和实现：

- `ChatMessage`：统一输入消息，包含 `messageId`、`chatId`、`senderId`、`text`。
- `ChatSession`：统一输出会话，提供 `sendText`、`sendStatus`、`sendError`。
- `ChatTransport`：平台入口，提供 `start(ChatMessageHandler)` 和 `stop()`。
- `ChatMessageHandler`：消息处理入口。
- `ChatAgentService`：将文本消息转换为 `Task("chat-" + messageId, text)`，创建带聊天 `RunLogger` 的 `AgentEngine` 并执行。
- `ApprovalManager`：保存待审批工具调用，处理 `/approve <id>` 和 `/reject <id>`，并限制只能由同一 `chatId` 审批。
- `ToolApprovalMiddleware`：连接 `ToolPermissionPolicy`、`ApprovalManager` 和 `ToolRegistry` Middleware。
- `WorkspaceSerialExecutor`：单线程队列，保证同一工作区同一时间只运行一个 Agent 任务。
- `AbstractChatRunLogger`：聊天平台 `RunLogger` 基类，默认回传最终回答和失败原因。

## Telegram Webhook 适配

`io.github.tinyclaw.agent.communication.telegram` 提供以下实现：

- `TelegramTransport`：使用 JDK `HttpServer` 启动本地 webhook endpoint，接收 Telegram POST update。
- `TelegramWebhookConfig`：读取 token、公网 webhook URL、本地监听地址、webhook path、secret token、注册延迟和重试等配置。
- `TelegramAgentConfig`：读取 Telegram Webhook 宿主的 Agent 运行配置，包括工作目录、最大步数、Thinking 开关、Plan Mode、服务端 debug 和工具权限配置。
- `TelegramWebhookRegistrar`：调用 Telegram Bot API `setWebhook`，设置 `allowed_updates=["message"]`，可选 `secret_token`。
- `TryCloudflareTunnel`：本地测试时启动 `cloudflared tunnel --url http://127.0.0.1:<port> --no-autoupdate`，解析临时 `trycloudflare.com` HTTPS URL。
- `TelegramAgentWebhookService`：库式宿主，组装 Telegram transport、trycloudflare 隧道、`ChatAgentService`、LM Studio Provider 和工具注册表。
- `TelegramSession`：调用 Bot API `sendMessage` 回传文本。
- `TelegramRunLogger`：把 `thinkingStarted`、`toolStarted`、`toolCompleted`、`finished`、`failed` 映射成 Telegram 可读消息。

本地 `HttpServer` 可以监听 HTTP；Telegram 官方注册的 webhook URL 必须是 HTTPS 公网地址。生产部署通常由反向代理、网关或隧道把 HTTPS 公网入口转发到本机 endpoint。

## 配置

默认运行读取项目根目录 `agent.properties`；如果根目录不存在，则读取 classpath 中的 `agent.properties`。应用启动不再支持额外的 JVM 覆盖参数或环境变量作为配置入口。

常用 properties key：

- `telegram.bot.token`：Telegram Bot Token，必填。
- `telegram.webhook.url`：HTTPS 公网 webhook URL；为空且未启用 trycloudflare 时只启动本地 server，不调用 `setWebhook`。
- `telegram.webhook.secret`：可选 secret token，用于校验 `X-Telegram-Bot-Api-Secret-Token`。
- `telegram.webhook.host`：本地监听地址，默认 `0.0.0.0`。
- `telegram.webhook.port`：本地监听端口，默认 `8080`。
- `telegram.webhook.path`：本地 webhook path，默认 `/telegram/webhook`。
- `telegram.webhook.tunnel`：可选，本地真 webhook 测试可设为 `trycloudflare`。
- `telegram.webhook.dropPendingUpdates`：可选，注册 webhook 时是否丢弃积压 update，默认 `false`。
- `telegram.webhook.maxConnections`：可选，传给 `setWebhook.max_connections`，默认 `40`。
- `telegram.webhook.registrationDelaySeconds`：可选，trycloudflare 动态 URL 注册前延迟秒数，默认 `60`。
- `telegram.webhook.registrationMaxAttempts`：可选，`setWebhook` 最大尝试次数（含首次），默认 `3`。
- `telegram.webhook.registrationRetryIntervalSeconds`：可选，`setWebhook` 重试间隔秒数，默认 `20`。
- `agent.workdir`：`TelegramAgentWebhookService` 的工作目录，默认 `.`。
- `agent.maxSteps`：Webhook 模式下 `AgentEngine` 最大步数，默认 `8`。
- `agent.enableThinking`：Webhook 模式是否开启 Thinking，默认 `false`。
- `agent.planMode`：Webhook 模式是否开启任务级状态外部化，默认 `false`。
- `agent.debug`：Webhook 模式是否把 Provider request / response / decision 摘要写入服务端 SLF4J 日志，默认 `false`；不发送到 Telegram 聊天窗口。
- `agent.permissions.enabled`：是否在 Telegram 模式启用工具审批 Middleware，默认 `false`。
- `agent.permissions.approvalTimeoutSeconds`：人工审批等待秒数，默认 `1800`。
- `agent.permissions.tool.<toolName>`：工具级权限动作，取值 `allow`、`ask`、`deny`。
- `agent.permissions.denyPattern.<n>`：高危正则；命中后直接 `deny`，优先级高于工具级动作。

Provider 配置沿用现有 `LmStudioConfig`、`SiliconFlowConfig` 等配置体系；当前 Telegram Webhook 宿主默认装配 `LmStudioModelProvider`。

## 消息流

```text
Telegram POST /telegram/webhook
  -> TelegramTransport
  -> ChatMessage
  -> ChatAgentService.handle
  -> approval command? ApprovalManager.resolveCommand
  -> WorkspaceSerialExecutor.submit
  -> AgentEngine.run(Task)
  -> ToolRegistry Middleware
  -> TelegramRunLogger / TelegramSession
```

执行规则：

- 非 POST 返回 405。
- secret token 不匹配返回 401，不调用 handler。
- malformed JSON 返回 400。
- 非文本 update 返回 200 并忽略，避免 Telegram 重复投递。
- handler 抛异常时通过 `TelegramSession` 回传 `消息处理失败：...`，HTTP 仍返回 200。
- 每条有效文本消息生成独立 `Task`，任务 ID 为 `chat-<messageId>`。
- `/approve <id>` 和 `/reject <id>` 在 `WorkspaceSerialExecutor.submit` 前处理，避免正在等待审批的 Agent 任务阻塞审批命令。
- 同工作区通过 `WorkspaceSerialExecutor` 串行执行；`AgentEngine` 内部只读工具并发策略保持不变。
- `agent.debug=true` 仅影响服务端 Provider 调试日志；`TelegramRunLogger` 仍只发送 thinking、tool、final、error 等用户可读状态。
- 启用权限审批后，`allow` 直接执行，`deny` 返回工具失败，`ask` 向同一 Telegram 会话发送审批 ID 并等待人工处理。
- 审批超时自动拒绝并清理内存 pending 状态。

## 本地 trycloudflare 真 Webhook 流程

本地验收不使用 `getUpdates` 轮询。推荐流程：

1. 确认 `cloudflared --version` 可执行。
2. 设置 `telegram.webhook.tunnel=trycloudflare`，并保持 `telegram.webhook.url` 为空。
3. 通过 `telegram` 子命令启动 `TelegramAgentWebhookService`。
4. 服务先启动本地 `HttpServer`。
5. 服务启动 `cloudflared tunnel --url http://127.0.0.1:<port> --no-autoupdate`。
6. 服务解析 `https://*.trycloudflare.com`，拼接 `<publicBaseUrl><webhookPath>`。
7. 服务可按配置等待 `telegram.webhook.registrationDelaySeconds`，再调用 `setWebhook`。
8. 注册失败时按 `telegram.webhook.registrationMaxAttempts` 重试，重试间隔为 `telegram.webhook.registrationRetryIntervalSeconds`。
9. Telegram 客户端发消息后，Telegram 通过 webhook POST 到 trycloudflare URL，再转发到本机。

如果显式配置了 `telegram.webhook.url=https://...`，服务不启动 trycloudflare，直接使用该 URL 注册 webhook；当前注册延迟/重试逻辑只在 trycloudflare 动态 URL 分支中执行。

## 测试策略

- `TelegramTransportTest`：覆盖合法文本、非文本忽略、secret token、malformed JSON、handler 异常仍 ACK。
- `TelegramWebhookConfigTest`：覆盖 properties 读取、默认值和 token 必填。
- `TelegramAgentConfigTest`：覆盖 `agent.workdir`、`agent.maxSteps`、`agent.enableThinking`、`agent.planMode`、`agent.debug`、工具权限配置的默认值、properties 读取和非法值。
- `TelegramWebhookRegistrarTest`：覆盖 `setWebhook` 请求体、空公网 URL 跳过注册、HTTP 错误、`ok=false`。
- `TryCloudflareTunnelTest`：覆盖 trycloudflare URL 解析和进程关闭。
- `TelegramAgentWebhookServiceTest`：覆盖本地 server、trycloudflare、动态 URL 注册、注册重试编排、权限 Middleware 挂载和 debug Provider 装配。
- `ApprovalManagerTest`：覆盖 approve、reject、超时清理、跨 chatId 拒绝和未知审批 ID。
- `ChatAgentServiceTest`、`WorkspaceSerialExecutorTest`、`TelegramRunLoggerTest`：保持通信调度、审批命令旁路、串行执行和日志映射覆盖。
- `AgentApplicationTest`：覆盖无参数缺命令、`telegram` 子命令、`run` 命令与未知命令行为。

## 非目标

- 不新增 Telegram 启动之外的 CLI 子命令。
- 不让 `run` 触发 Telegram Webhook 启动。
- 不支持 `telegram --debug`；Telegram 长驻入口的调试开关统一走 `agent.debug`。
- 不在通信层解析或维护长程会话记忆；Plan Mode 只把 chat-scoped 状态目录传给 PromptComposer。
- 不为权限规则做运行时热加载。
- 不保留 Long Polling / `getUpdates` 路径。
- 不把 `deleteWebhook` / `getUpdates` 作为正式 webhook 验收路径。
- 不新增复杂任务队列、数据库或工作流引擎。
