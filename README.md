# Tiny Agent Harness

Tiny Agent Harness 是一个用于学习和验证 Agent Harness 运行时边界的 Java 项目。它保持实现足够小：模型负责决策，Harness 负责主循环、工具执行、上下文约束、日志和失败收口。

Tiny Agent Harness is a compact Java project for exploring Agent Harness runtime boundaries. The model makes decisions; the harness owns the loop, tool execution, context constraints, logging, and failure handling.

## 功能 / Features

- Main Loop：支持 `FinishDecision`、`ToolDecision`、`ParallelToolDecision`、可选 `ThinkingDecision`。
- Provider：内置 OpenAI-compatible Chat Completions 适配；应用默认装配 `LmStudioModelProvider`。
- Tool Registry：内置 `read_file`、`write_file`、`edit_file`、`bash`、`spawn_subagent`。
- Context：读取标准 `AGENTS.md`，扫描 `.tinyclaw/skills/**/SKILL.md` 的 `name` / `description` 摘要。
- Working Memory：Telegram 长驻入口按会话保留进程内短期记录，默认 12 条消息 / 12000 字符。
- Context Compaction：Provider 请求前压缩超长 observation，不污染真实工具输出或 Session 记录。
- Plan Mode：可选把长程任务状态外部化到 `.tinyclaw/state/.../PLAN.md` 和 `TODO.md`。
- Telegram Webhook：支持 `telegram` 子命令、trycloudflare、Webhook 接收、工具审批和服务端 debug 日志。
- Subagent：`spawn_subagent` 同步拉起受限子 Agent，用于隔离探索上下文并返回精炼报告。
- Benchmark：`bench` 子命令会创建独立靶场、运行 Agent、执行验证命令并输出 JSON 报告。
- Observability：`RunLogger`、`RunResult`、`RunMetrics` 和 `TraceRecorder` 分别承担可读日志、最终结果、汇总指标和 JSON trace。

English summary:

- Main loop with finish, single-tool, parallel-tool, and optional thinking decisions.
- OpenAI-compatible providers; CLI and Telegram currently default to `LmStudioModelProvider`.
- Minimal built-in tools: `read_file`, `write_file`, `edit_file`, `bash`, and `spawn_subagent`.
- Standard project instructions from `AGENTS.md`, plus skill summaries from `.tinyclaw/skills/**/SKILL.md`.
- In-memory working memory for Telegram sessions, plus optional file-based Plan Mode state.
- Telegram Webhook support with optional tool approval and provider debug summaries.
- Benchmark runner with isolated workspaces, validation commands, metrics, and JSON reports.

## 项目结构 / Project Layout

```text
src/main/java/io/github/tinyclaw/agent
  app/             CLI entry and application wiring
  benchmark/       benchmark cases, runner, and report writer
  communication/   chat abstraction, Telegram Webhook, approval flow
  context/         System Prompt composition, AGENTS.md, skill summaries
  domain/          Task, Decision, ToolCall, AgentContext data model
  observability/   local JSON trace recording
  provider/        OpenAI-compatible model provider implementations
  runtime/         AgentEngine, RunLogger, RunResult, memory, compaction, metrics
  tool/            tool interface, registry, built-in tools, permissions

src/test/java/io/github/tinyclaw/agent
  app/ architecture/ benchmark/ communication/ context/ observability/ provider/ runtime/ tool/

docs/
  design notes and architecture constraints
```

## 环境要求 / Requirements

- JDK 21
- Maven 3.9+

运行前确认当前 shell 使用 Java 21：

```sh
java -version
mvn -version
```

## 快速开始 / Quick Start

在项目根目录创建 `agent.properties`，内容可参考 `src/main/resources/agent.properties.example`。配置 OpenAI-compatible 服务，例如 LM Studio：

```properties
lmstudio.baseUrl=http://localhost:1234/v1
lmstudio.model=your-local-model
```

编译和测试：

```sh
mvn -q -DskipTests compile
mvn test
```

传递 Maven 系统属性时，确保 `-D...` 作为完整参数传入：

```sh
mvn "-Dtest=AgentApplicationTest" test
```

## CLI 运行 / CLI Usage

正式入口有 `run`、`telegram` 和 `bench`。

执行一次 prompt：

```sh
mvn exec:java -Dexec.args="run --prompt <prompt>"
```

开启 debug 摘要：

```sh
mvn exec:java -Dexec.args="run --debug --prompt <prompt>"
```

开启两阶段 Thinking：

```sh
mvn exec:java -Dexec.args="run --thinking --max-steps 8 --prompt <prompt>"
```

开启 Plan Mode：

```sh
mvn exec:java -Dexec.args="run --plan --prompt <prompt>"
```

CLI `run` 结束时会输出 `METRICS`，包含模型调用、Token、模型耗时、工具调用和工具耗时。CLI Plan Mode 状态目录为 `.tinyclaw/state/cli/default/`。

## Benchmark

执行内置 Benchmark：

```sh
mvn exec:java -Dexec.args="bench"
```

`bench` 会为每个用例创建独立 `.tinyclaw/bench/workspaces/...` 靶场，运行 Agent 后执行验证命令判卷，并把 JSON 报告写入 `.tinyclaw/bench/reports/`。报告包含成功率、Token、模型耗时、工具耗时、工具失败次数和完成轮数。

## Telegram Webhook

启动 Telegram Webhook：

```sh
mvn exec:java -Dexec.args="telegram"
```

常用配置在 `agent.properties` 中维护：

```properties
agent.workdir=.
agent.maxSteps=8
agent.enableThinking=false
agent.planMode=false
agent.debug=false
agent.workingMemory.maxMessages=12
agent.workingMemory.maxChars=12000
agent.permissions.enabled=false
agent.permissions.file=.tinyclaw/permissions.yaml

telegram.bot.token=your-telegram-bot-token
telegram.webhook.host=127.0.0.1
telegram.webhook.port=8080
telegram.webhook.path=/telegram/webhook
telegram.webhook.tunnel=trycloudflare
telegram.webhook.url=
telegram.webhook.secret=your-random-secret
```

- `telegram` 不支持 `telegram --debug`；服务端 Provider debug 通过 `agent.debug=true` 开启。
- `telegram.webhook.url` 为空且 `telegram.webhook.tunnel=trycloudflare` 时，会启动 trycloudflare 隧道并注册动态 HTTPS URL。
- 同一个 `chatId` 复用同一段进程内会话记录，不同 `chatId` 彼此隔离。
- 发送 `/usage` 可查看当前会话累计模型调用、Token、模型耗时、工具调用和工具耗时。
- 开启 `agent.planMode=true` 后，每个 chat 使用 `.tinyclaw/state/chat/<chatId>/`。
- 开启 `agent.permissions.enabled=true` 后，可通过 `.tinyclaw/permissions.yaml` 配置 `allow`、`ask`、`deny` 规则；`ask` 规则会在 Telegram 会话中等待 `/approve <id>` 或 `/reject <id>`。

最小权限 YAML 示例：

```yaml
version: 1
enabled: true
defaultAction: ask
approvalTimeoutSeconds: 1800

rules:
  - id: allow-read
    tools: [read_file]
    action: allow

  - id: deny-dangerous-bash
    tools: [bash]
    action: deny
    arguments:
      command:
        regex: "(?i)\\b(rm\\s+-rf|sudo\\b|drop\\s+(database|table)|kubectl\\s+delete)\\b"

  - id: ask-write-tools
    tools: [write_file, edit_file, bash]
    action: ask
```

## Provider

当前已有两个 OpenAI-compatible Provider：

- `LmStudioModelProvider`：CLI 和 Telegram 默认装配，读取 `lmstudio.baseUrl` 与 `lmstudio.model`。
- `SiliconFlowModelProvider`：读取 `siliconflow.apiKey`、`siliconflow.baseUrl` 与 `siliconflow.model`；已有测试覆盖，但当前没有 CLI 参数直接切换默认装配。

LM Studio live 测试：

```sh
mvn "-Dlmstudio.live=true" "-Dtest=LmStudioModelProviderLiveTest" test
```

SiliconFlow live 测试：

```sh
mvn "-Dsiliconflow.live=true" "-Dtest=SiliconFlowModelProviderLiveTest" test
```

## 文档 / Docs

- [Agent Harness 设计原则](docs/agent-harness-principles.md)
- [Main Loop 规划](docs/main-loop-plan.md)
- [Provider 设计](docs/provider-design.md)
- [Tool Registry 设计](docs/tool-registry-design.md)
- [通信服务抽象与 Telegram Webhook 接入设计](docs/communication-service-design.md)
- [Telegram Webhook 工作原理](docs/telegram-webhook-principles.md)
- [State Externalization 设计](docs/state-externalization-design.md)
- [Context Compaction 设计](docs/context-compaction-design.md)
