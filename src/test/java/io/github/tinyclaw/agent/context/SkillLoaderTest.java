package io.github.tinyclaw.agent.context;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SkillLoaderTest {

    @TempDir
    Path workDir;

    @Test
    void loadsOnlySkillSummariesFromTinyClawSkills() throws Exception {
        Path skillDir = workDir.resolve(".tinyclaw").resolve("skills").resolve("java");
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("SKILL.md"), "---\n"
                + "name: java-style\n"
                + "description: Use when editing Java code.\n"
                + "---\n"
                + "\n"
                + "# Detailed body should stay out of summary\n", StandardCharsets.UTF_8);

        List<Skill> skills = new SkillLoader(workDir).loadSummaries();

        assertThat(skills).hasSize(1);
        assertThat(skills.get(0).name()).isEqualTo("java-style");
        assertThat(skills.get(0).description()).isEqualTo("Use when editing Java code.");
        assertThat(skills.get(0).path()).endsWith(Path.of(".tinyclaw", "skills", "java", "SKILL.md"));
    }

    @Test
    void returnsEmptyWhenSkillsDirectoryIsMissing() {
        List<Skill> skills = new SkillLoader(workDir).loadSummaries();

        assertThat(skills).isEmpty();
    }
}
