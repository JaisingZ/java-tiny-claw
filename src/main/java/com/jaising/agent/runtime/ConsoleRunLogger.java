package com.jaising.agent.runtime;

import com.jaising.agent.domain.FinishDecision;
import com.jaising.agent.domain.ThinkingDecision;
import com.jaising.agent.domain.ToolCall;
import com.jaising.agent.domain.ToolDecision;
import com.jaising.agent.domain.ToolDefinition;
import com.jaising.agent.tool.ToolResult;
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

    public ConsoleRunLogger(PrintStream output) {
        this(output, true);
    }

    public ConsoleRunLogger(PrintStream output, boolean verboseEvents) {
        this.output = output;
        this.verboseEvents = verboseEvents;
    }

    public static ConsoleRunLogger standardOutput(boolean verboseEvents) {
        return new ConsoleRunLogger(
                new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8),
                verboseEvents);
    }

    @Override
    public void writeLine(String line) {
        output.println(line);
    }

    @Override
    public void writeBlankLine() {
        output.println();
    }

    @Override
    public void registryMounted(String toolName) {
        logEvent("[Registry] 成功挂载工具: " + toolName);
    }

    @Override
    public void engineStarted(Path workDir, String model, int maxSteps, boolean enableThinking,
            List<ToolDefinition> tools) {
        logEvent("[Engine] 引擎启动，锁定工作区: " + workDir.toAbsolutePath().normalize());
        logEvent("[Engine] 模型: " + model);
        logEvent("[Engine] 慢思考模式 (Thinking Phase): " + enableThinking);
        logEvent("[Engine] 最大工具步数: " + maxSteps);
        logEvent("[Engine] 可用工具: " + toolNames(tools));
    }

    @Override
    public void turnStarted(int turn) {
        if (!verboseEvents) {
            return;
        }
        writeBlankLine();
        logEvent("========== [Turn " + turn + "] 开始 ==========");
    }

    @Override
    public void thinkingStarted() {
        logEvent("[Engine][Phase 1] 隐藏工具，进入慢思考...");
    }

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

    @Override
    public void actionStarted(List<ToolDefinition> tools) {
        logEvent("[Engine][Phase 2] 恢复工具挂载，等待模型采取行动...");
        logEvent("[Engine][Phase 2] 可见工具: " + toolNames(tools));
    }

    @Override
    public void toolDecision(ToolDecision decision) {
        logEvent("[Engine] 模型请求调用工具: " + decision.call().toolName());
    }

    @Override
    public void toolStarted(ToolCall call) {
        logEvent("  -> 🛠️ 执行工具: " + call.toolName() + ", 参数: " + call.arguments());
    }

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

    @Override
    public void finished(FinishDecision decision) {
        if (!verboseEvents) {
            return;
        }
        logEvent("[Engine] 模型未请求调用工具，任务宣告完成。");
        writeLine("🤖 [最终回复]:");
        writeLine(decision.answer());
    }

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
