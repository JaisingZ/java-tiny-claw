package io.github.tinyclaw.agent.context;

import io.github.tinyclaw.agent.domain.DecisionPhase;
import io.github.tinyclaw.agent.domain.ToolDefinition;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Prompt 组装输入上下文。
 */
public final class PromptContext {

    private final Path workDir;
    private final DecisionPhase phase;
    private final List<ToolDefinition> availableTools;

    /**
     * 创建 Prompt 组装上下文。
     */
    public PromptContext(Path workDir, DecisionPhase phase, List<ToolDefinition> availableTools) {
        this.workDir = Objects.requireNonNull(workDir, "workDir");
        this.phase = Objects.requireNonNull(phase, "phase");
        List<ToolDefinition> safeTools = availableTools == null
                ? Collections.<ToolDefinition>emptyList()
                : availableTools;
        this.availableTools = Collections.unmodifiableList(new ArrayList<ToolDefinition>(safeTools));
    }

    /**
     * 当前工作区路径。
     */
    public Path workDir() {
        return workDir;
    }

    /**
     * 当前模型决策阶段。
     */
    public DecisionPhase phase() {
        return phase;
    }

    /**
     * 当前可用工具定义。
     */
    public List<ToolDefinition> availableTools() {
        return availableTools;
    }
}
