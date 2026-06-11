# Tiny Agent Harness

`Tiny Agent Harness` 是一个用于学习和验证 Agent Harness 的 Java 项目。它聚焦一个可控、可测试、可回放的 Main Loop：读取任务上下文，请求模型决策，执行工具或结束任务，并返回最终 `RunResult`。

## 主要功能

- Main Loop：支持 `FinishDecision`、`ToolDecision`、`ParallelToolDecision` 和失败收口。
- Two-stage ReAct：可选开启 `THINKING -> ACTION` 两阶段循环。
- OpenAI-compatible Provider：默认示例使用本地 OpenAI 兼容 Chat Completions 服务。
- 工具系统：内置 `read_file`、`write_file`、`edit_file`、`bash`、`spawn_subagent`。
- 运行时上下文：`AgentContext` 仅在内存中保存当前步进上下文。
- 错误自愈：工具失败会作为观测写回上下文，并附带可执行恢复建议。
- 系统防呆：重复无效工具调用会触发 `[SYSTEM REMINDER]` 近因提醒，推动模型换策略或求助。
- Plan Mode：可选将长程任务状态外部化到 `.tinyclaw/state/.../PLAN.md` 与 `TODO.md`。
- Telegram 工具审批：Webhook 模式可选启用 `allow / ask / deny` 工具权限和人工审批。
- Subagent 委派：主 Agent 可通过 `spawn_subagent` 同步拉起受限子 Agent，隔离探索上下文并只接收精炼报告。
- RunLogger：输出可读运行日志；CLI `--debug` 会额外输出 Provider 摘要，Telegram `agent.debug=true` 只把 Provider 摘要写入服务端日志。
- RunResult：保留最终决策结果、可读观测和本轮运行指标。
- 可观测性：按运行和 Session 统计模型调用耗时、Provider 返回的 Token usage、工具调用耗时。

## 项目结构

```text
src/main/java/io/github/tinyclaw/agent
  app/          命令行入口和应用装配
  benchmark/    自动化 Benchmark 用例、Runner 和报告
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

## Subagent 委派

`spawn_subagent` 是一个普通工具，不引入复杂 Agent Graph：

- 主 Agent 调用 `spawn_subagent` 并传入 `task_prompt`。
- Runtime 拉起一次性子 `AgentEngine`，使用干净上下文，不继承父 Session / Working Memory。
- 子 Agent v1 只挂载 `read_file` 和 `bash`，不会获得 `write_file`、`edit_file` 或再次委派 `spawn_subagent` 的能力。
- 子 Agent 最大步数固定为 6，禁用 Thinking；结束后只把纯文本探索报告返回给主 Agent。
- 多个 `spawn_subagent` 出现在同一轮并行工具调用时，会复用现有只读并发调度，最终观测仍按模型声明顺序聚合。

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

正式启动入口有 `run`、`telegram` 和 `bench` 三种。

执行 prompt：

```sh
mvn exec:java -Dexec.args="run --prompt \"请直接回答：OK。不要调用工具。\""
```

CLI `run` 结束时会额外输出一行 `METRICS`，包含模型调用次数、Prompt/Completion/Total Tokens、usage 缺失次数、模型耗时、工具调用次数和工具耗时。

执行长程任务并开启 Plan Mode：

```sh
mvn exec:java -Dexec.args="run --plan --prompt \"重构这个模块并更新测试。\""
```

CLI Plan Mode 使用 `.tinyclaw/state/cli/default/` 保存任务级 `PLAN.md` 与 `TODO.md`。

执行内置 Benchmark：

```sh
mvn exec:java -Dexec.args="bench"
```

`bench` 会为每个用例创建独立 `.tinyclaw/bench/workspaces/...` 靶场，运行 Agent 后执行验证命令判卷，并把 JSON 报告写入 `.tinyclaw/bench/reports/`。报告包含成功率、Token、模型耗时、工具耗时、工具失败次数和完成轮数。

启动 Telegram Webhook：

```sh
mvn exec:java -Dexec.args="telegram"
```

`telegram` 是显式通信服务子命令，不支持 `telegram --debug`；服务端调试日志通过 `agent.debug` 配置开启。

本地真 Webhook 测试可使用 trycloudflare：

```properties
agent.workdir=.
agent.maxSteps=8
agent.enableThinking=false
agent.planMode=false
agent.debug=false
agent.workingMemory.maxMessages=12
agent.workingMemory.maxChars=12000
agent.permissions.enabled=false
agent.permissions.approvalTimeoutSeconds=1800
agent.permissions.file=.claw/permissions.yaml
agent.permissions.hotReload=true
agent.permissions.reloadIntervalSeconds=2
agent.permissions.tool.read_file=allow
agent.permissions.tool.write_file=ask
agent.permissions.tool.edit_file=ask
agent.permissions.tool.bash=ask
agent.permissions.denyPattern.1=(?i)\brm\s+-rf\b
agent.permissions.denyPattern.2=(?i)\bRemove-Item\b.*\b(-Recurse|-Force)\b
agent.permissions.denyPattern.3=(?i)\bsudo\b
agent.permissions.denyPattern.4=(?i)\bdrop\s+(database|table)\b
agent.permissions.denyPattern.5=(?i)\bkubectl\s+delete\b

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
- 发送 `/usage` 可查看当前会话累计模型调用次数、Token、模型耗时、工具调用次数和工具耗时。
- 开启 `agent.planMode=true` 后，每个 `chatId` 使用 `.tinyclaw/state/chat/<chatId>/` 保存任务级状态文件。
- 每次请求模型前只截取最近 Working Memory，默认最多 12 条消息、12000 字符。
- Session 不落盘，进程重启后历史清空；Plan Mode 的 `PLAN.md` / `TODO.md` 是独立的任务级文件状态。

Telegram 模式设置 `agent.debug=true` 时，只把 Provider request / response / decision 摘要写入服务端 SLF4J 日志，不发送到聊天窗口。

Telegram 模式可选开启工具审批：

- `agent.permissions.enabled=false` 默认关闭；CLI `run` 不挂载审批中间件。
- 推荐把具体权限规则写入 `agent.permissions.file` 指向的 YAML，默认 `.claw/permissions.yaml`。
- YAML 支持 `allow`、`ask`、`deny`，规则可按工具名和参数正则匹配，冲突时 `deny > ask > allow`。
- 启用后，`deny` 直接拒绝；`ask` 工具会向同一 Telegram 会话发送审批 ID。
- 在同一会话回复 `/approve <id>` 放行，回复 `/reject <id>` 拒绝。
- 超过 `agent.permissions.approvalTimeoutSeconds` 未处理会自动拒绝并清理内存状态。
- `agent.permissions.hotReload=true` 时会监听 YAML 修改，解析成功后新工具调用使用新策略；解析失败时继续使用上一份有效策略。
- 旧 `agent.permissions.tool.*` 和 `agent.permissions.denyPattern.*` 仍作为无 YAML 文件时的兼容 fallback。

`.claw/permissions.yaml` 示例：

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
- `telegram` 模式通过 Telegram 状态消息和服务端日志观察链路，不支持 `telegram --debug`；Provider 调试摘要使用 `agent.debug=true`。
- 非 debug 模式会继续打印 `OBSERVATIONS` 和 `RESULT`。
- Token 只使用 Provider/API 返回的 `usage`；Provider 不返回时 Token 记为 0，并在指标里累计为 unavailable。
- 当前版本不计算人民币或美元成本，后续可基于模型价格表扩展。
- 工具失败不会立即终止主循环；失败观测会进入下一轮上下文，无法自愈时由 `maxSteps` 收口。
- `[SYSTEM REMINDER]` 是运行时动态 observation，不是静态 System Prompt；它只在连续无效调用达到阈值时注入。
- 当前版本为精简实现，不提供 Java 侧持久化状态机或结构化 trace 层。
- `RunLogger` 是面向人的运行日志来源，`RunResult` 是最终输出结果。
