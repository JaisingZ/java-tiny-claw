# Tiny Agent Harness

`Tiny Agent Harness` 是一个用于学习和验证 Agent Harness 的 Java 项目。它聚焦一个可控、可测试、可回放的 Main Loop：读取任务上下文，请求模型决策，执行工具或结束任务，并返回最终 `RunResult`。

## 主要功能

- Main Loop：支持 `FinishDecision`、`ToolDecision`、`ParallelToolDecision` 和失败收口。
- Two-stage ReAct：可选开启 `THINKING -> ACTION` 两阶段循环。
- OpenAI-compatible Provider：默认示例使用本地 OpenAI 兼容 Chat Completions 服务。
- 工具系统：内置 `read_file`、`write_file`、`edit_file`、`bash`。
- 运行时上下文：`AgentContext` 仅在内存中保存当前步进上下文。
- 错误自愈：工具失败会作为观测写回上下文，并附带可执行恢复建议。
- Plan Mode：可选将长程任务状态外部化到 `.tinyclaw/state/.../PLAN.md` 与 `TODO.md`。
- RunLogger：输出可读运行日志；`--debug` 时包含更详细事件与 Provider 请求/响应摘要。
- RunResult：保留最终决策结果与可读观测。

## 项目结构

```text
src/main/java/io/github/tinyclaw/agent
  app/          命令行入口和应用装配
  runtime/      AgentEngine、RunLogger、RunResult
  context/      系统提示词组装与上下文约束
  provider/     ModelProvider 和 OpenAI-compatible Provider 实现
  tool/         工具接口、注册表和内置工具
  communication/ 可选通信适配
  domain/       Task、Decision、ToolCall、AgentContext 相关对象

docs/           设计说明、Main Loop 规划和 Telegram Webhook 工作原理
scripts/        可选本地辅助脚本
```

Telegram Webhook 工作原理见 [docs/telegram-webhook-principles.md](docs/telegram-webhook-principles.md)。
状态外部化设计见 [docs/state-externalization-design.md](docs/state-externalization-design.md)。

运行时每轮请求模型前会先经过 `PromptComposer` 生成 `systemPrompt`，并注入 Provider：

- `AGENTS.md`（标准文件名，仅支持 `AGENTS.md`，不读取 `AGENT.md`）
- 当前阶段约束（THINKING/ACTION）与运行约束
- `SKILL.md` 外挂能力摘要（存在时追加）

## 环境要求

- JDK 21
- Maven

## 配置 Provider

将 `src/main/resources/agent.properties.example` 复制为项目根目录下的 `agent.properties`。

确保本地 OpenAI 兼容 Chat Completions 服务已启动并加载目标模型，然后填写：

```properties
lmstudio.baseUrl=http://localhost:1234/v1
lmstudio.model=your-local-model
```

日常运行默认读取项目根目录下的 `agent.properties`；如果根目录不存在，则读取 classpath 中的 `agent.properties`。

## 构建与测试

```sh
mvn test
```

只编译不跑测试：

```sh
mvn -q -DskipTests compile
```

## Provider live 测试

验证当前 OpenAI 兼容 Provider 配置：

```sh
mvn "-Dlmstudio.live=true" "-Dtest=LmStudioModelProviderLiveTest" test
```

## 命令行运行

不带子命令启动会提示缺少命令：

```sh
mvn exec:java
```

正式启动入口只有 `run` 和 `telegram` 两种。

执行 prompt：

```sh
mvn exec:java -Dexec.args="run --prompt \"请直接回答：OK。不要调用工具。\""
```

执行长程任务并开启 Plan Mode：

```sh
mvn exec:java -Dexec.args="run --plan --prompt \"重构这个模块并更新测试。\""
```

CLI Plan Mode 使用 `.tinyclaw/state/cli/default/` 保存任务级 `PLAN.md` 与 `TODO.md`。

启动 Telegram Webhook：

```sh
mvn exec:java -Dexec.args="telegram"
```

`telegram` 是显式通信服务子命令，不接受 `--debug` 等额外参数。

本地真 Webhook 测试可使用 trycloudflare：

```properties
agent.workdir=.
agent.maxSteps=8
agent.enableThinking=false
agent.planMode=false
agent.workingMemory.maxMessages=12
agent.workingMemory.maxChars=12000

telegram.bot.token=your-telegram-bot-token
telegram.webhook.host=127.0.0.1
telegram.webhook.port=8080
telegram.webhook.path=/telegram/webhook
telegram.webhook.tunnel=trycloudflare
telegram.webhook.url=
telegram.webhook.secret=your-random-secret
telegram.webhook.dropPendingUpdates=false
telegram.webhook.maxConnections=40
telegram.webhook.registrationDelaySeconds=60
telegram.webhook.registrationMaxAttempts=3
telegram.webhook.registrationRetryIntervalSeconds=20
```

固定公网 HTTPS 入口可改用 `telegram.webhook.url=https://.../telegram/webhook`，并清空 `telegram.webhook.tunnel`。

Telegram Webhook 是长驻入口，会按消息来源维护进程内 Session：

- 同一个 `chatId` 复用同一段会话历史。
- 不同 `chatId` 的历史彼此隔离。
- 开启 `agent.planMode=true` 后，每个 `chatId` 使用 `.tinyclaw/state/chat/<chatId>/` 保存任务级状态文件。
- 每次请求模型前只截取最近 Working Memory，默认最多 12 条消息、12000 字符。
- Session 不落盘，进程重启后历史清空；Plan Mode 的 `PLAN.md` / `TODO.md` 是独立的任务级文件状态。

开启可读调试日志：

```sh
mvn exec:java -Dexec.args="run --debug --prompt \"请直接回答：OK。不要调用工具。\""
```

开启两阶段慢思考：

```sh
mvn exec:java -Dexec.args="run --debug --thinking --max-steps 8 --prompt \"请创建文件 target/Hello.java，用 bash 编译并运行它，要求输出 Hello。\""
```

## 注意事项

- `bash` 工具会按运行平台选择可用 shell。
- `write_file` 会自动创建父目录，并以 UTF-8 写入文本文件。
- 创建或覆盖源码应优先使用 `write_file`，避免由 shell 重定向带来的编码差异。
- `run --debug` 输出实时可读日志和 Provider JSON 摘要，末尾会打印 `RESULT`。
- `telegram` 模式通过 Telegram 状态消息和控制台日志观察链路，不走 `--debug` 参数。
- 非 debug 模式会继续打印 `OBSERVATIONS` 和 `RESULT`。
- 工具失败不会立即终止主循环；失败观测会进入下一轮上下文，无法自愈时由 `maxSteps` 收口。
- 当前版本为精简实现，不提供 Java 侧持久化状态机或结构化 trace 层。
- `RunLogger` 是面向人的运行日志来源，`RunResult` 是最终输出结果。
