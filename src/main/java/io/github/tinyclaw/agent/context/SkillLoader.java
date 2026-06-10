package io.github.tinyclaw.agent.context;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * 加载工作区外挂 Skill 摘要。
 */
public final class SkillLoader {

    private static final String SKILLS_DIR = ".tinyclaw/skills";
    private static final String SKILL_FILE = "SKILL.md";

    private final Path workDir;

    /**
     * 创建 Skill 加载器。
     */
    public SkillLoader(Path workDir) {
        this.workDir = Objects.requireNonNull(workDir, "workDir");
    }

    /**
     * 扫描 .tinyclaw/skills 下的 SKILL.md，仅返回 name/description 摘要。
     */
    public List<Skill> loadSummaries() {
        Path skillsDir = workDir.resolve(SKILLS_DIR);
        if (!Files.isDirectory(skillsDir)) {
            return new ArrayList<Skill>();
        }
        List<Skill> skills = new ArrayList<Skill>();
        try (Stream<Path> paths = Files.walk(skillsDir)) {
            paths.filter(path -> Files.isRegularFile(path) && SKILL_FILE.equals(path.getFileName().toString()))
                    .sorted(Comparator.comparing(Path::toString))
                    .forEach(path -> addSkill(skills, path));
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to scan skills: " + skillsDir, ex);
        }
        return skills;
    }

    private void addSkill(List<Skill> skills, Path skillFile) {
        try {
            String content = Files.readString(skillFile, StandardCharsets.UTF_8);
            String name = frontMatterValue(content, "name");
            String description = frontMatterValue(content, "description");
            if (hasText(name) && hasText(description)) {
                skills.add(new Skill(name, description, skillFile));
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read skill: " + skillFile, ex);
        }
    }

    private String frontMatterValue(String content, String key) {
        if (!hasText(content) || !content.startsWith("---")) {
            return "";
        }
        String[] lines = content.split("\\R");
        for (int index = 1; index < lines.length; index++) {
            String line = lines[index].trim();
            if ("---".equals(line)) {
                return "";
            }
            int separator = line.indexOf(':');
            if (separator > 0 && key.equals(line.substring(0, separator).trim())) {
                return stripQuotes(line.substring(separator + 1).trim());
            }
        }
        return "";
    }

    private String stripQuotes(String value) {
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
