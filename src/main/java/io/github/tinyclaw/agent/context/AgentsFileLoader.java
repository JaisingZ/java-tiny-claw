package io.github.tinyclaw.agent.context;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * 加载工作区级 AGENTS.md 指令。
 */
public final class AgentsFileLoader {

    private static final String AGENTS_FILE = "AGENTS.md";

    private final Path workDir;

    /**
     * 创建 AGENTS.md 加载器。
     */
    public AgentsFileLoader(Path workDir) {
        this.workDir = Objects.requireNonNull(workDir, "workDir");
    }

    /**
     * 只读取标准 AGENTS.md；文件不存在时返回空字符串。
     */
    public String load() {
        Path agentsFile = workDir.resolve(AGENTS_FILE);
        if (!Files.isRegularFile(agentsFile)) {
            return "";
        }
        try {
            return Files.readString(agentsFile, StandardCharsets.UTF_8).trim();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read AGENTS.md: " + agentsFile, ex);
        }
    }
}
