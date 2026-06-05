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
                Collections.<ToolDefinition>emptyList()));

        assertThat(prompt)
                .contains("当前是 ACTION 阶段")
                .contains("调用一个或多个独立工具")
                .contains("function.arguments");
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
