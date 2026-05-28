# java-tiny-claw

`java-tiny-claw` 是一个用于学习和验证 Agent Harness 的 Java 项目。它聚焦一个可控、可测试、可审计的 Main Loop：读取任务状态，请求模型决策，执行工具或结束任务，保存状态并记录 trace。

## 主要功能

- Main Loop：支持 `FinishDecision`、`ToolDecision` 和失败收口。
- Two-stage ReAct：可选开启 `THINKING -> ACTION` 两阶段循环。
- SiliconFlow Provider：使用 OpenAI 兼容 Chat Completions 协议调用模型。
- 工具系统：内置 `read_file`、`write_file`、`edit_file`、`bash`。
- Middleware：支持工具执行前拦截，例如 allow-list。
- StateStore：支持内存状态和文件状态存储。
- TraceRecorder：记录结构化运行轨迹，便于测试和审计。
- RunLogger：`--debug` 时输出可读运行日志和 Provider 请求/响应。
- 启动自检：通过真实 `AgentApplication startup-check` 验证 Main Loop 核心能力。

## 项目结构

```text
src/main/java/com/jaising/agent
  app/          命令行入口和启动自检
  runtime/      AgentEngine、RunLogger、RunResult
  provider/     ModelProvider 和 SiliconFlow 实现
  tool/         工具接口、注册表和内置工具
  middleware/   工具执行前拦截
  state/        状态存储
  trace/        结构化 trace 事件记录
  domain/       Task、AgentState、Decision 等领域对象

docs/           设计说明和 Main Loop 规划
scripts/        一键启动自检脚本
```

## 环境要求

- JDK 21
- Maven
- Windows PowerShell

脚本会优先使用：

```powershell
C:\Program Files\BellSoft\LibericaJDK-21
```

## 配置 SiliconFlow

复制示例配置：

```powershell
Copy-Item src\main\resources\agent.properties.example src\main\resources\agent.properties
```

填写 `siliconflow.apiKey`：

```properties
siliconflow.apiKey=你的 API Key
siliconflow.baseUrl=https://api.siliconflow.cn/v1
siliconflow.model=Qwen/Qwen3-8B
```

不要提交真实 API Key。

## 构建与测试

```powershell
mvn test
```

只编译不跑测试：

```powershell
mvn -q -DskipTests compile
```

## 启动自检

默认离线自检，不依赖真实模型：

```powershell
.\scripts\run-main-loop-startup.ps1
```

追加真实 SiliconFlow 链路验证：

```powershell
.\scripts\run-main-loop-startup.ps1 -Live
```

## 命令行运行

先构建项目和运行时 classpath：

```powershell
mvn -q -DskipTests package
mvn -q -DincludeScope=runtime dependency:build-classpath "-Dmdep.outputFile=target\runtime-classpath.txt"
$dependencyClasspath = (Get-Content 'target\runtime-classpath.txt' -Raw).Trim()
$cp = "target\classes;$dependencyClasspath"
```

查看 harness 信息：

```powershell
java -cp $cp com.jaising.agent.app.AgentApplication
```

执行 prompt：

```powershell
java -cp $cp com.jaising.agent.app.AgentApplication run --prompt "请直接回答：OK。不要调用工具。"
```

开启可读调试日志：

```powershell
java -cp $cp com.jaising.agent.app.AgentApplication run --debug --prompt "请直接回答：OK。不要调用工具。"
```

开启两阶段慢思考：

```powershell
java -cp $cp com.jaising.agent.app.AgentApplication run --debug --thinking --max-steps 8 --prompt "请创建文件 target/Hello.java，用 bash 编译并运行它，要求输出 Hello，Java！"
```

## 注意事项

- `bash` 工具在 Windows 下实际执行 PowerShell，不是 Linux bash。
- PowerShell 5 不支持 `&&` / `||`；多步命令应检查 `$LASTEXITCODE`。
- `write_file` 会自动创建父目录，并以 UTF-8 写入文本文件。
- 创建或覆盖 Java 源码应优先使用 `write_file`，不要用 PowerShell `Set-Content` / `Out-File` 写源码。
- `run --debug` 输出实时人类可读日志和 Provider JSON，末尾只打印 `RESULT`；非 debug 模式会打印 `TRACE / RESULT / OBSERVATIONS`。
- `TraceRecorder` 是结构化审计链路，`RunLogger` 是面向人的实时日志，两者职责不同。
