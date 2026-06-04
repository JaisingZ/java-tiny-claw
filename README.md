# Tiny Agent Harness

`Tiny Agent Harness` 是一个用于学习和验证 Agent Harness 的 Java 项目。它聚焦一个可控、可测试、可回放的 Main Loop：读取任务上下文，请求模型决策，执行工具或结束任务，并返回最终 `RunResult`。

## 主要功能

- Main Loop：支持 `FinishDecision`、`ToolDecision`、`ParallelToolDecision` 和失败收口。
- Two-stage ReAct：可选开启 `THINKING -> ACTION` 两阶段循环。
- OpenAI-compatible Provider：默认示例使用本地 OpenAI 兼容 Chat Completions 服务。
- 工具系统：内置 `read_file`、`write_file`、`edit_file`、`bash`。
- 运行时上下文：`AgentContext` 仅在内存中保存当前步进上下文。
- RunLogger：输出可读运行日志；`--debug` 时包含更详细事件与 Provider 请求/响应摘要。
- RunResult：保留最终决策结果与可读观测。
- 启动自检：通过 `AgentApplication startup-check` 验证 Main Loop 核心能力。

## 项目结构

```text
src/main/java/io/github/tinyclaw/agent
  app/          命令行入口和启动自检
  runtime/      AgentEngine、RunLogger、RunResult
  provider/     ModelProvider 和 OpenAI-compatible Provider 实现
  tool/         工具接口、注册表和内置工具
  communication/ 可选通信适配
  domain/       Task、Decision、ToolCall、AgentContext 相关对象

docs/           设计说明、Main Loop 规划和 Telegram Webhook 工作原理
scripts/        可选本地辅助脚本
```

Telegram Webhook 工作原理见 [docs/telegram-webhook-principles.md](docs/telegram-webhook-principles.md)。

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

也可以通过系统属性指定配置文件：

```sh
mvn exec:java -Dagent.config=/path/to/agent.properties -Dexec.args="run --prompt \"请直接回答：OK。不要调用工具。\""
```

## 构建与测试

```sh
mvn test
```

只编译不跑测试：

```sh
mvn -q -DskipTests compile
```

## 启动自检

默认离线自检，不依赖真实模型：

```sh
mvn exec:java -Dexec.args="startup-check"
```

追加真实 Provider 链路验证：

```sh
mvn exec:java -Dexec.args="startup-check --live"
```

`--live` 会额外使用当前 `agent.properties` 中的 OpenAI 兼容 Provider 配置，跑一轮真实 Provider 链路。典型输出形态：

```text
=== Main Loop Startup Check ===
live=true
CASE single-stage-finish
RESULT status=SUCCESS answer=single-stage-ready failure=null
CASE two-stage-thinking-action
RESULT status=SUCCESS answer=thinking-action-ready failure=null
CASE real-tools-write-bash
RESULT status=SUCCESS answer=java sample executed failure=null
CASE failure-missing-tool
RESULT status=FAILED answer=null failure=Unknown tool: missing
CASE failure-tool-error
RESULT status=FAILED answer=null failure=startup tool failed
CASE failure-provider-error
RESULT status=FAILED answer=null failure=provider_error: startup provider failed
CASE failure-max-steps
RESULT status=FAILED answer=null failure=max_steps_exceeded
CASE live-provider
RESULT status=SUCCESS answer=Main Loop live 启动测试完成。 failure=null
=== Main Loop Startup Check: PASSED ===
```

也可以只跑 Provider 的 live 测试：

```sh
mvn "-Dlmstudio.live=true" "-Dtest=LmStudioModelProviderLiveTest" test
```

## 命令行运行

查看 harness 信息：

```sh
mvn exec:java
```

执行 prompt：

```sh
mvn exec:java -Dexec.args="run --prompt \"请直接回答：OK。不要调用工具。\""
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
- 非 debug 模式会继续打印 `OBSERVATIONS` 和 `RESULT`。
- 当前版本为精简实现，不提供持久化状态层或结构化 trace 层。
- `RunLogger` 是面向人的运行日志来源，`RunResult` 是最终输出结果。
