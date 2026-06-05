package io.github.tinyclaw.agent.context;

import io.github.tinyclaw.agent.domain.DecisionPhase;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * 默认 System Prompt 组装器。
 */
public final class DefaultPromptComposer implements PromptComposer {

    private final Path workDir;
    private final AgentsFileLoader agentsFileLoader;
    private final SkillLoader skillLoader;

    /**
     * 创建默认 Prompt 组装器。
     */
    public DefaultPromptComposer(Path workDir) {
        this(workDir, new AgentsFileLoader(workDir), new SkillLoader(workDir));
    }

    DefaultPromptComposer(Path workDir, AgentsFileLoader agentsFileLoader, SkillLoader skillLoader) {
        this.workDir = Objects.requireNonNull(workDir, "workDir");
        this.agentsFileLoader = Objects.requireNonNull(agentsFileLoader, "agentsFileLoader");
        this.skillLoader = Objects.requireNonNull(skillLoader, "skillLoader");
    }

    @Override
    public String compose(PromptContext context) {
        StringBuilder prompt = new StringBuilder();
        prompt.append(minimalCore());
        prompt.append(environmentInstruction());
        prompt.append(phaseInstruction(context.phase()));
        appendAgentsFile(prompt);
        appendSkills(prompt);
        return prompt.toString().trim();
    }

    private String minimalCore() {
        return "# Core Identity\n"
                + "你是 Tiny Agent Harness 的模型 Provider，请根据当前任务给出下一步决策。\n"
                + "始终使用简体中文回复，保持简洁，遵守 KISS 原则。\n"
                + "你可以通过系统提供的工具读取、创建、修改和执行工作区中的代码。\n\n";
    }

    private String environmentInstruction() {
        return "# Runtime Environment\n"
                + "运行环境事实：bash 工具在 Windows 下实际执行 powershell -NoProfile -NonInteractive -Command。\n"
                + "Do not use && or || in PowerShell commands.\n"
                + "Do not use ; to mean run the next command only when the previous command succeeds.\n"
                + "多步命令必须检查 $LASTEXITCODE。\n"
                + "创建或覆盖 Java 源码必须优先使用 write_file，源码必须是 UTF-8 文本，不能包含 UTF-16 或 NUL 字节。\n"
                + "不要用 PowerShell Set-Content 或 Out-File 写 Java 源码。\n"
                + "编译并运行 target/Hello.java 的推荐模板："
                + "javac target/Hello.java; if ($LASTEXITCODE -eq 0) { java -cp target Hello } else { exit $LASTEXITCODE }。\n\n";
    }

    private String phaseInstruction(DecisionPhase phase) {
        if (phase == DecisionPhase.THINKING) {
            return "# Decision Phase\n"
                    + "当前是 THINKING 阶段：只输出内部计划，不要回答用户，不要调用工具。\n"
                    + "内部计划最多3条，每条一句话，总长度不要超过120个中文字符。\n"
                    + "内部计划必须基于已有 Observation，不能把已失败命令再次作为候选方案。\n\n";
        }
        return "# Decision Phase\n"
                + "当前是 ACTION 阶段：必须输出最终回答，或在需要时调用一个或多个独立工具；不要输出空内容。\n"
                + "最终回答必须直接面向用户，禁止输出思考过程，禁止输出分析，禁止输出解释，禁止输出计划，禁止输出推理，禁止输出英文说明。\n"
                + "不要复述用户要求、系统约束或 Observation 原文。\n"
                + "若用户要求一句话、一个标题、一个路径或一个简短结果，就只输出一句话或该结果本身。\n"
                + "如果多个操作互相独立（例如读取多个不同文件），建议在单轮中并行调用。\n"
                + "如果 Observation 已经满足用户目标且没有失败信息，直接输出最终回答，不要重复调用相同工具。\n"
                + "调用工具时 function.arguments 必须是完整闭合的严格 JSON object，不能使用 markdown、注释、自然语言包裹或尾随说明。\n"
                + "write_file 会自动创建父目录，创建文件前不要额外调用 mkdir。\n\n";
    }

    private void appendAgentsFile(StringBuilder prompt) {
        String agents = agentsFileLoader.load();
        if (!hasText(agents)) {
            return;
        }
        prompt.append("# Project Instructions (AGENTS.md)\n");
        prompt.append(agents).append("\n\n");
    }

    private void appendSkills(StringBuilder prompt) {
        List<Skill> skills = skillLoader.loadSummaries();
        if (skills.isEmpty()) {
            return;
        }
        prompt.append("# Available Skills\n");
        prompt.append("以下是工作区提供的外挂技能摘要。仅当任务匹配 description 时遵循对应 Skill。\n");
        for (Skill skill : skills) {
            prompt.append("- ").append(skill.name()).append(": ")
                    .append(skill.description()).append("\n");
        }
        prompt.append("\n");
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
