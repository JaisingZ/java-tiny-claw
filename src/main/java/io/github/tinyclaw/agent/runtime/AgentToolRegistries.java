package io.github.tinyclaw.agent.runtime;

import io.github.tinyclaw.agent.provider.ModelProvider;
import io.github.tinyclaw.agent.tool.BashTool;
import io.github.tinyclaw.agent.tool.EditFileTool;
import io.github.tinyclaw.agent.tool.ReadFileTool;
import io.github.tinyclaw.agent.tool.SubagentTool;
import io.github.tinyclaw.agent.tool.ToolRegistry;
import io.github.tinyclaw.agent.tool.WriteFileTool;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Agent 默认工具注册表工厂。
 */
public final class AgentToolRegistries {

    private AgentToolRegistries() {
    }

    public static ToolRegistry mainRegistry(ModelProvider provider, Path workDir) {
        Objects.requireNonNull(provider, "provider");
        Path root = normalize(workDir);
        return new ToolRegistry()
                .register(new ReadFileTool(root))
                .register(new WriteFileTool(root))
                .register(new EditFileTool(root))
                .register(new BashTool(root))
                .register(new SubagentTool(new DefaultSubagentRunner(provider, root)));
    }

    public static ToolRegistry subagentRegistry(Path workDir) {
        return new ToolRegistry()
                .register(new ReadFileTool(normalize(workDir)));
    }

    private static Path normalize(Path workDir) {
        return workDir == null ? Path.of(".") : workDir;
    }
}
