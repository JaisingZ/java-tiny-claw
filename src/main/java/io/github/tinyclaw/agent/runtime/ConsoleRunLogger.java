package io.github.tinyclaw.agent.runtime;

import io.github.tinyclaw.agent.domain.FinishDecision;
import io.github.tinyclaw.agent.domain.ThinkingDecision;
import io.github.tinyclaw.agent.domain.ToolCall;
import io.github.tinyclaw.agent.domain.ToolDecision;
import io.github.tinyclaw.agent.domain.ToolDefinition;
import io.github.tinyclaw.agent.tool.ToolResult;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 控制台运行日志
 * 输出可读的主循环执行进展
 */
public final class ConsoleRunLogger implements RunLogger {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");

    private final PrintStream output;
    private final boolean verboseEvents;

    /**
     * 创建默认输出详细事件的控制台日志。
     */
    public ConsoleRunLogger(PrintStream output) {
        this(output, true);
    }

    /**
     * 创建可控制事件详细程度的控制台日志。
     */
    public ConsoleRunLogger(PrintStream output, boolean verboseEvents) {
        this.output = output;
        this.verboseEvents = verboseEvents;
    }

    /**
     * 创建使用 UTF-8 标准输出的控制台日志。
     */
    public static ConsoleRunLogger standardOutput(boolean verboseEvents) {
        return new ConsoleRunLogger(
                new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8),
                verboseEvents);
    }

    /**
     * 直接输出一行文本。
     */
    @Override
    public void writeLine(String line) {
        output.println(line);
    }

    /**
     * 直接输出空行。
     */
    @Override
    public void writeBlankLine() {
        output.println();
    }

    /**
     * 记录工具已挂载到注册表。
     */
    @Override
    public void registryMounted(String toolName) {
        logEvent("[Registry] 成功挂载工具: " + toolName);
    }

    /**
     * 记录主循环启动配置。
     */
    @Override
    public void engineStarted(Path workDir, String model, int maxSteps, boolean enableThinking,
            List<ToolDefinition> tools) {
        logEvent("[Engine] 引擎启动，锁定工作区: " + workDir.toAbsolutePath().normalize());
        logEvent("[Engine] 模型: " + model);
        logEvent("[Engine] 慢思考模式 (Thinking Phase): " + enableThinking);
        logEvent("[Engine] 最大工具步数: " + maxSteps);
        logEvent("[Engine] 可用工具: " + toolNames(tools));
    }

    /**
     * 记录一个 turn 开始。
     */
    @Override
    public void turnStarted(int turn) {
        if (!verboseEvents) {
            return;
        }
        writeBlankLine();
        logEvent("========== [Turn " + turn + "] 开始 ==========");
    }

    /**
     * 记录 thinking 阶段开始。
     */
    @Override
    public void thinkingStarted() {
        logEvent("[Engine][Phase 1] 隐藏工具，进入慢思考...");
    }

    /**
     * 记录 thinking 阶段完成和思考文本。
     */
    @Override
    public void thinkingCompleted(ThinkingDecision decision, long durationMillis) {
        if (!verboseEvents) {
            return;
        }
        logEvent("[Engine][Phase 1] 慢思考完成 (返回 " + textLength(decision.thought())
                + " 字符, 耗时 " + durationMillis + "ms)");
        writeLine("🧠 [内部思考]:");
        writeLine(decision.thought());
    }

    /**
     * 记录 action 阶段开始和可见工具。
     */
    @Override
    public void actionStarted(List<ToolDefinition> tools) {
        logEvent("[Engine][Phase 2] 恢复工具挂载，等待模型采取行动...");
        logEvent("[Engine][Phase 2] 可见工具: " + toolNames(tools));
    }

    /**
     * 记录模型选择的单工具调用。
     */
    @Override
    public void toolDecision(ToolDecision decision) {
        logEvent("[Engine] 模型请求调用工具: " + decision.call().toolName());
    }

    /**
     * 记录工具开始执行。
     */
    @Override
    public void toolStarted(ToolCall call) {
        logEvent("  -> 🛠️ 执行工具: " + call.toolName() + ", 参数: " + call.arguments());
    }

    /**
     * 记录工具执行结果。
     */
    @Override
    public void toolCompleted(ToolCall call, ToolResult result, long durationMillis) {
        if (!verboseEvents) {
            return;
        }
        if (result.success()) {
            logEvent("  -> ✅ 工具执行成功 (返回 " + utf8Length(result.output())
                    + " 字节, 耗时 " + durationMillis + "ms)");
            return;
        }
        logEvent("  -> ❌ 工具执行失败: " + result.errorMessage()
                + " (耗时 " + durationMillis + "ms)");
    }

    /**
     * 记录模型最终回答。
     */
    @Override
    public void finished(FinishDecision decision) {
        if (!verboseEvents) {
            return;
        }
        logEvent("[Engine] 模型未请求调用工具，任务宣告完成。");
        writeLine("🤖 [最终回复]:");
        writeLine(decision.answer());
    }

    /**
     * 记录主循环失败原因。
     */
    @Override
    public void failed(String reason) {
        logEvent("[Engine] 任务失败: " + reason);
    }

    private void logEvent(String message) {
        if (!verboseEvents) {
            return;
        }
        writeLine(LocalDateTime.now().format(FORMATTER) + " " + message);
    }

    private List<String> toolNames(List<ToolDefinition> tools) {
        return tools.stream().map(ToolDefinition::name).toList();
    }

    private int utf8Length(String value) {
        if (value == null) {
            return 0;
        }
        return value.getBytes(StandardCharsets.UTF_8).length;
    }

    private int textLength(String value) {
        if (value == null) {
            return 0;
        }
        return value.length();
    }
}
