package io.github.tinyclaw.agent.context;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.tinyclaw.agent.domain.DecisionPhase;
import io.github.tinyclaw.agent.domain.ToolDefinition;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DefaultPromptComposerTest {

    @TempDir
    Path workDir;

    @Test
    void includesMinimalCoreEnvironmentPhaseAndAgentsFile() throws Exception {
        Files.writeString(workDir.resolve("AGENTS.md"), "- 遵守 KISS", StandardCharsets.UTF_8);
        DefaultPromptComposer composer = new DefaultPromptComposer(workDir);

        String prompt = composer.compose(new PromptContext(workDir, DecisionPhase.ACTION,
                Collections.<ToolDefinition>emptyList()));

        assertThat(prompt)
                .contains("Tiny Agent Harness")
                .contains("简体中文")
                .contains("powershell -NoProfile -NonInteractive -Command")
                .contains("当前是 ACTION 阶段")
                .contains("Project Instructions")
                .contains("- 遵守 KISS");
    }

    @Test
    void thinkingPhaseDoesNotExposeToolCallInstructions() {
        DefaultPromptComposer composer = new DefaultPromptComposer(workDir);

        String prompt = composer.compose(new PromptContext(workDir, DecisionPhase.THINKING,
                Collections.<ToolDefinition>emptyList()));

        assertThat(prompt)
                .contains("当前是 THINKING 阶段")
                .doesNotContain("调用一个或多个独立工具")
                .doesNotContain("function.arguments");
    }

    @Test
    void actionPhaseIncludesToolCallInstructions() {
        DefaultPromptComposer composer = new DefaultPromptComposer(workDir);

        String prompt = composer.compose(new PromptContext(workDir, DecisionPhase.ACTION,
                Collections.singletonList(new ToolDefinition("bash", "run command",
                        Collections.<String, Object>singletonMap("type", "object")))));

        assertThat(prompt)
                .contains("当前是 ACTION 阶段")
                .contains("调用一个或多个独立工具")
                .contains("function.arguments")
                .contains("验证通过")
                .contains("直接总结")
                .contains("刚修改文件")
                .contains("优先执行验证")
                .contains("不要重复读取刚读过且未变化的文件");
    }

    @Test
    void reviewPhaseRequiresStrictJsonAndNoToolInstructions() {
        DefaultPromptComposer composer = new DefaultPromptComposer(workDir);

        String prompt = composer.compose(new PromptContext(workDir, DecisionPhase.REVIEW,
                Collections.<ToolDefinition>emptyList()));

        assertThat(prompt)
                .contains("当前是 REVIEW 阶段")
                .contains("严格 JSON object")
                .contains("APPROVED")
                .contains("REVISE")
                .contains("BLOCKED")
                .contains("approvedPlan 不超过1200字符")
                .doesNotContain("function.arguments")
                .doesNotContain("调用一个或多个独立工具");
    }

    @Test
    void actionPhaseWithoutToolsOnlyAllowsFinalAnswer() {
        DefaultPromptComposer composer = new DefaultPromptComposer(workDir);

        String prompt = composer.compose(new PromptContext(workDir, DecisionPhase.ACTION,
                Collections.<ToolDefinition>emptyList()));

        assertThat(prompt)
                .contains("当前是 ACTION 阶段")
                .contains("无工具可用")
                .contains("只输出最终回答")
                .doesNotContain("function.arguments")
                .doesNotContain("调用一个或多个独立工具");
    }

    @Test
    void planModeIsDisabledByDefault() {
        DefaultPromptComposer composer = new DefaultPromptComposer(workDir);

        String prompt = composer.compose(new PromptContext(workDir, DecisionPhase.ACTION,
                Collections.<ToolDefinition>emptyList()));

        assertThat(prompt)
                .doesNotContain("Plan Mode: State Externalization")
                .doesNotContain("PLAN.md")
                .doesNotContain("TODO.md");
    }

    @Test
    void planModeInjectsStateExternalizationRules() {
        DefaultPromptComposer composer = new DefaultPromptComposer(workDir, true,
                Path.of(".tinyclaw", "state", "cli", "default"));

        String prompt = composer.compose(new PromptContext(workDir, DecisionPhase.ACTION,
                Collections.<ToolDefinition>emptyList()));

        assertThat(prompt)
                .contains("Plan Mode: State Externalization")
                .contains("状态目录（相对于当前工作区）：.tinyclaw/state/cli/default")
                .contains("PLAN.md")
                .contains("TODO.md")
                .contains("任务开始或需要恢复进度时")
                .contains("如果文件不存在")
                .contains("如果文件已存在")
                .contains("更新 TODO.md 的 checkbox")
                .contains("最终总结")
                .contains("不要重复读取代码或状态文件")
                .contains("最终回答必须说明实际完成了什么")
                .doesNotContain("ACTION 阶段开始长程任务时，先检查状态目录");
    }

    @Test
    void thinkingPhaseWithPlanModeStillDoesNotExposeToolCallInstructions() {
        DefaultPromptComposer composer = new DefaultPromptComposer(workDir, true,
                Path.of(".tinyclaw", "state", "cli", "default"));

        String prompt = composer.compose(new PromptContext(workDir, DecisionPhase.THINKING,
                Collections.<ToolDefinition>emptyList()));

        assertThat(prompt)
                .contains("当前是 THINKING 阶段")
                .contains("Plan Mode: State Externalization")
                .doesNotContain("调用一个或多个独立工具")
                .doesNotContain("function.arguments");
    }

    @Test
    void includesSkillSummariesWithoutSkillBody() throws Exception {
        Path skillDir = workDir.resolve(".tinyclaw").resolve("skills").resolve("java");
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("SKILL.md"), "---\n"
                + "name: java-style\n"
                + "description: Use when editing Java code.\n"
                + "---\n"
                + "\n"
                + "Detailed body should not be injected.\n", StandardCharsets.UTF_8);
        DefaultPromptComposer composer = new DefaultPromptComposer(workDir);

        String prompt = composer.compose(new PromptContext(workDir, DecisionPhase.ACTION,
                Collections.<ToolDefinition>emptyList()));

        assertThat(prompt)
                .contains("Available Skills")
                .contains("java-style")
                .contains("Use when editing Java code.")
                .doesNotContain("Detailed body should not be injected.");
    }
}
