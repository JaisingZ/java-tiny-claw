package com.jaising.agent.runtime;

import com.jaising.agent.domain.FinishDecision;
import com.jaising.agent.domain.ThinkingDecision;
import com.jaising.agent.domain.ToolCall;
import com.jaising.agent.domain.ToolDecision;
import com.jaising.agent.domain.ToolDefinition;
import com.jaising.agent.tool.ToolResult;
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

    public ConsoleRunLogger(PrintStream output) {
        this.output = output;
    }

    @Override
    public void registryMounted(String toolName) {
        log("[Registry] 成功挂载工具: " + toolName);
    }

    @Override
    public void engineStarted(Path workDir, String model, int maxSteps, boolean enableThinking,
            List<ToolDefinition> tools) {
        log("[Engine] 引擎启动，锁定工作区: " + workDir.toAbsolutePath().normalize());
        log("[Engine] 模型: " + model);
        log("[Engine] 慢思考模式 (Thinking Phase): " + enableThinking);
        log("[Engine] 最大工具步数: " + maxSteps);
        log("[Engine] 可用工具: " + toolNames(tools));
    }

    @Override
    public void turnStarted(int turn) {
        output.println();
        log("========== [Turn " + turn + "] 开始 ==========");
    }

    @Override
    public void thinkingStarted() {
        log("[Engine][Phase 1] 隐藏工具，进入慢思考...");
    }

    @Override
    public void thinkingCompleted(ThinkingDecision decision, long durationMillis) {
        log("[Engine][Phase 1] 慢思考完成 (返回 " + textLength(decision.thought())
                + " 字符, 耗时 " + durationMillis + "ms)");
        output.println("🧠 [内部思考]:");
        output.println(decision.thought());
    }

    @Override
    public void actionStarted(List<ToolDefinition> tools) {
        log("[Engine][Phase 2] 恢复工具挂载，等待模型采取行动...");
        log("[Engine][Phase 2] 可见工具: " + toolNames(tools));
    }

    @Override
    public void toolDecision(ToolDecision decision) {
        log("[Engine] 模型请求调用工具: " + decision.call().toolName());
    }

    @Override
    public void toolStarted(ToolCall call) {
        log("  -> 🛠️ 执行工具: " + call.toolName() + ", 参数: " + call.arguments());
    }

    @Override
    public void toolCompleted(ToolCall call, ToolResult result, long durationMillis) {
        if (result.success()) {
            log("  -> ✅ 工具执行成功 (返回 " + utf8Length(result.output())
                    + " 字节, 耗时 " + durationMillis + "ms)");
            return;
        }
        log("  -> ❌ 工具执行失败: " + result.errorMessage()
                + " (耗时 " + durationMillis + "ms)");
    }

    @Override
    public void finished(FinishDecision decision) {
        log("[Engine] 模型未请求调用工具，任务宣告完成。");
        output.println("🤖 [最终回复]:");
        output.println(decision.answer());
    }

    @Override
    public void failed(String reason) {
        log("[Engine] 任务失败: " + reason);
    }

    private void log(String message) {
        output.println(LocalDateTime.now().format(FORMATTER) + " " + message);
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
