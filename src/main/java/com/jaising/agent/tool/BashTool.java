package com.jaising.agent.tool;

import com.jaising.agent.domain.AgentContext;
import com.jaising.agent.domain.ToolCall;
import com.jaising.agent.domain.ToolDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 在工作区执行 shell 命令的工具
 */
public final class BashTool implements Tool {

    private static final Logger logger = LoggerFactory.getLogger(BashTool.class);

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
    private static final int DEFAULT_MAX_OUTPUT_CHARS = 8_000;

    private final Path workDir;
    private final Duration timeout;
    private final int maxOutputChars;

    /**
     * 创建 bash 工具
     */
    public BashTool(Path workDir) {
        this(workDir, DEFAULT_TIMEOUT, DEFAULT_MAX_OUTPUT_CHARS);
    }

    /**
     * 创建 bash 工具
     */
    public BashTool(Path workDir, Duration timeout, int maxOutputChars) {
        this.workDir = workDir.toAbsolutePath().normalize();
        this.timeout = timeout;
        this.maxOutputChars = maxOutputChars;
    }

    /**
     * 返回工具名称。
     */
    @Override
    public String name() {
        return "bash";
    }

    /**
     * 返回提供给模型的工具定义。
     */
    @Override
    public ToolDefinition definition() {
        Map<String, Object> commandProperty = new LinkedHashMap<String, Object>();
        commandProperty.put("type", "string");
        commandProperty.put("description", commandDescription());

        Map<String, Object> properties = new LinkedHashMap<String, Object>();
        properties.put("command", commandProperty);

        Map<String, Object> parameters = new LinkedHashMap<String, Object>();
        parameters.put("type", "object");
        parameters.put("properties", properties);
        parameters.put("required", Arrays.asList("command"));

        return new ToolDefinition(name(), toolDescription(), parameters);
    }

    /**
     * 执行命令。
     */
    @Override
    public ToolResult execute(ToolCall call, AgentContext state) {
        Object rawCommand = call.arguments().get("command");
        if (!(rawCommand instanceof String) || ((String) rawCommand).trim().isEmpty()) {
            logger.warn("BashTool execution failed: missing command argument");
            return ToolResult.failure("Missing required argument: command");
        }

        String command = (String) rawCommand;
        logger.info("Executing bash command: {}", command);
        Process process;
        try {
            process = newProcess(command).start();
        } catch (IOException ex) {
            logger.error("Failed to start bash process", ex);
            return ToolResult.failure("Failed to start command: " + ex.getMessage());
        }

        CompletableFuture<String> outputFuture = CompletableFuture.supplyAsync(
                () -> readOutput(process.getInputStream()));
        boolean finished;
        try {
            finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            return ToolResult.failure("Command interrupted");
        }

        if (!finished) {
            process.destroyForcibly();
            return ToolResult.success(formatOutput(outputFuture.join()
                    + "\n[Command timed out after " + timeout.toMillis() + " ms and was terminated]"));
        }

        String output = outputFuture.join();
        int exitCode = process.exitValue();
        if (exitCode != 0) {
            return ToolResult.success(formatOutput("exitCode=" + exitCode + "\n" + output));
        }
        if (output.trim().isEmpty()) {
            return ToolResult.success("Command executed successfully with no output");
        }
        return ToolResult.success(formatOutput(output));
    }

    private ProcessBuilder newProcess(String command) {
        ProcessBuilder builder;
        if (isWindows()) {
            builder = new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-Command", command);
        } else {
            builder = new ProcessBuilder("bash", "-c", command);
        }
        builder.directory(workDir.toFile());
        builder.redirectErrorStream(true);
        return builder;
    }

    private String readOutput(InputStream inputStream) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            inputStream.transferTo(output);
            return output.toString(StandardCharsets.UTF_8);
        } catch (IOException ex) {
            return "Failed to read command output: " + ex.getMessage();
        }
    }

    private String formatOutput(String output) {
        if (output.length() <= maxOutputChars) {
            return output;
        }
        return output.substring(0, maxOutputChars)
                + "\n\n[Output truncated to " + maxOutputChars + " characters]";
    }

    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

    private String toolDescription() {
        if (isWindows()) {
            return "Execute a PowerShell command inside the workspace. Do not use mkdir -p; "
                    + "use New-Item -ItemType Directory -Force target when a directory must be created. "
                    + "Do not use && or ||. Do not rely on ; for success gating; "
                    + "check $LASTEXITCODE before running the next command.";
        }
        return "Execute a bash command inside the workspace";
    }

    private String commandDescription() {
        if (isWindows()) {
            return "PowerShell command to execute inside the workspace through "
                    + "powershell -NoProfile -NonInteractive -Command. "
                    + "For compile-then-run, use: javac target/Hello.java; "
                    + "if ($LASTEXITCODE -eq 0) { java -cp target Hello } else { exit $LASTEXITCODE }";
        }
        return "bash command to execute inside the workspace";
    }
    /**
     * 默认标记为具有副作用，因为脚本内容不可知。
     */
    @Override
    public boolean isSideEffect() {
        return true;
    }
}
