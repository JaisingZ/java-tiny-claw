package io.github.tinyclaw.agent.runtime;

import io.github.tinyclaw.agent.context.DefaultPromptComposer;
import io.github.tinyclaw.agent.context.PromptComposer;
import io.github.tinyclaw.agent.context.PromptContext;
import io.github.tinyclaw.agent.domain.Task;
import io.github.tinyclaw.agent.provider.ModelProvider;
import io.github.tinyclaw.agent.tool.SubagentRunner;
import io.github.tinyclaw.agent.tool.ToolRegistry;
import io.github.tinyclaw.agent.tool.ToolResult;
import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;

/**
 * 默认子智能体运行器。
 * 使用一次性干净上下文和只读 read_file 工具集。
 */
public final class DefaultSubagentRunner implements SubagentRunner {

    private static final int SUBAGENT_MAX_STEPS = 6;
    private static final String SUBAGENT_SYSTEM_SUFFIX = "\n\n# Subagent Constraints\n"
            + "你是 Explorer Subagent，只负责受限探索。\n"
            + "必须基于工具证据回答；没有证据就继续读取相关文件或说明未找到。\n"
            + "最终输出纯文本报告，交给主 Agent 汇总。\n"
            + "不要写文件，不要假设，不要请求 write_file、edit_file、bash 或 spawn_subagent。\n";

    private final ModelProvider provider;
    private final Path workDir;
    private final PromptComposer promptComposer;

    /**
     * 创建使用默认 prompt composer 的子智能体运行器。
     */
    public DefaultSubagentRunner(ModelProvider provider, Path workDir) {
        this(provider, workDir, new DefaultPromptComposer(normalize(workDir)));
    }

    /**
     * 创建使用指定 prompt composer 的子智能体运行器。
     */
    public DefaultSubagentRunner(ModelProvider provider, Path workDir, PromptComposer promptComposer) {
        this.provider = Objects.requireNonNull(provider, "provider");
        this.workDir = normalize(workDir);
        this.promptComposer = promptComposer == null
                ? new DefaultPromptComposer(this.workDir)
                : promptComposer;
    }

    @Override
    public ToolResult run(String taskPrompt) {
        if (taskPrompt == null || taskPrompt.trim().isEmpty()) {
            return ToolResult.failure("Missing required argument: task_prompt");
        }

        ToolRegistry registry = AgentToolRegistries.subagentRegistry(workDir);
        AgentEngine engine = new AgentEngine(provider, registry, SUBAGENT_MAX_STEPS, false,
                NoopRunLogger.INSTANCE, withSubagentInstruction(promptComposer), workDir);
        try {
            RunResult result = engine.run(new Task("subagent-" + UUID.randomUUID(), taskPrompt.trim()));
            if (result.status() == RunStatus.SUCCESS) {
                return ToolResult.success(result.finalAnswer());
            }
            return ToolResult.failure(result.failureReason() == null ? "unknown failure" : result.failureReason());
        } catch (RuntimeException ex) {
            String message = ex.getMessage();
            return ToolResult.failure(message == null || message.trim().isEmpty() ? "unknown failure" : message);
        } finally {
            engine.shutdown();
        }
    }

    private PromptComposer withSubagentInstruction(PromptComposer delegate) {
        return new PromptComposer() {
            @Override
            public String compose(PromptContext context) {
                return delegate.compose(context) + SUBAGENT_SYSTEM_SUFFIX;
            }
        };
    }

    private static Path normalize(Path path) {
        return path == null ? Path.of(".").toAbsolutePath().normalize() : path.toAbsolutePath().normalize();
    }
}
