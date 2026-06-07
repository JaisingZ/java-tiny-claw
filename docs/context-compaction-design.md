# Context Compaction 设计

## 目标

`Context Compaction` 属于 Runtime 层，负责在每次请求 `ModelProvider` 前压缩临时上下文，避免单条超大工具输出撑爆模型上下文窗口。

当前项目的上下文控制分两层：

- `WorkingMemoryPolicy` 是第一道窗口控制，负责从 `AgentSession.history()` 中选择哪些历史消息进入 `AgentContext.workingMemory()`。
- `ContextCompactor` 是第二道请求前压缩，负责在 `ModelProvider.decide(...)` 前处理已经进入本轮上下文的超长 observation。

两者都不修改 `AgentSession.history()`。Session 历史仍保存真实消息，压缩只影响本次发送给模型的临时上下文。

## 与 Working Memory 的关系

当前数据流如下：

```text
AgentSession.history()
  -> WorkingMemoryPolicy.apply(...)
  -> AgentContext.workingMemory()
  -> ContextCompactor.compact(...)
  -> ModelProvider.decide(...)
```

`WorkingMemoryPolicy` 的职责是控制历史窗口大小。它从 Session 历史尾部开始截取消息，并同时受最大消息数和最大字符数约束；如果窗口开头变成孤立的 `OBSERVATION`，会继续丢弃该 observation，避免模型看到没有前置语境的工具结果。

`ContextCompactor` 的职责是控制 Provider 请求载荷。即使 Working Memory 已经限制了历史窗口，当前轮 `context.observations()` 或窗口内某条 `OBSERVATION` 仍可能过长；这类内容在进入 Provider 前会被 masking 或 head-tail 截断。

`WorkingMemoryPolicy.DEFAULT_MAX_CHARS` 和 `ContextCompactionPolicy.maxContextChars` 默认都是 `12_000`，但作用阶段不同：前者决定历史消息是否进入本轮上下文，后者决定本轮上下文是否需要在请求模型前压缩。

## 边界

Compaction 只处理发送给模型的 `AgentContext` 副本。

- 不修改 `AgentSession.history()`。
- 不修改工具真实输出。
- 不修改 `RunResult.observations()`。
- 不修改 `ModelProvider` 接口。
- 不在 LM Studio 或 SiliconFlow Provider 内重复实现压缩。

System Prompt 仍由 `PromptComposer` 生成并独立传给 Provider，不进入 compactor。Provider 继续负责厂商协议适配，Runtime 负责请求前的上下文控制。

## 默认策略

`ContextCompactionPolicy` 使用字符数作为 v1 水位线，避免引入 tokenizer 或额外模型调用。

```text
maxContextChars      = 12000
retainRecentMessages = 6
maxObservationChars  = 1000
headChars            = 500
tailChars            = 500
maskThresholdChars   = 200
```

参数必须为正数，并且 `headChars + tailChars <= maxObservationChars`。

## 压缩规则

当估算总字符数未超过 `maxContextChars` 时，直接使用原始上下文。

当超过水位线时：

1. `USER` 和 `ASSISTANT` working memory 默认保留，避免破坏对话语义。
2. 远期 `OBSERVATION` 若超过 `maskThresholdChars`，替换为 masking 文本，只保留“早期工具输出存在”和原始长度。
3. 近期 `OBSERVATION` 若超过 `maxObservationChars`，保留头尾，中间替换为截断说明。
4. 当前轮 `context.observations()` 若超过 `maxObservationChars`，同样执行 head-tail 截断。

这里的远期与近期按 `retainRecentMessages` 从 working memory 尾部计算。当前轮 observations 永远按近期处理，因为它们刚由工具执行产生，模型下一步通常需要知道首尾线索。

## 限制与后续演进

当前版本不做：

- 真实 token 计数。
- LLM 摘要压缩。
- 向量检索或 memory paging。
- 文件系统持久化记忆。
- `read_file` 按行读取参数扩展。

后续若 Provider 能返回 usage，可把固定字符水位线升级为基于真实 prompt tokens 的自适应压缩。若要引入摘要，应在后台或 StateStore/Memory 层做，不应每次 Provider 调用前同步请求另一个模型。

## 测试方案

单元测试覆盖：

- `ContextCompactionPolicy` 默认值与非法参数校验。
- 未超过总字符水位线时不压缩。
- 远期 `OBSERVATION` 被 masking。
- 近期 `OBSERVATION` 被 head-tail 截断。
- 当前轮 observations 超长也会截断。
- `USER` / `ASSISTANT` working memory 默认保留。
- 原始 `AgentContext` 和 Session 历史不被污染。

主循环测试覆盖：

- 工具返回超长输出后，下一次 Provider 收到压缩后的 observation。
- `RunResult.observations()` 和 Session 回写仍保留原始完整输出。
- Thinking 与 Action 阶段都通过压缩后的上下文请求 Provider。

验收命令：

```powershell
mvn -q "-Dtest=ContextCompactionPolicyTest,ContextCompactorTest,AgentEngineTest,AgentSessionTest" test
mvn test
```
