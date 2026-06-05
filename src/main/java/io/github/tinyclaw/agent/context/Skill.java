package io.github.tinyclaw.agent.context;

import java.nio.file.Path;
import java.util.Objects;

/**
 * 可注入 Prompt 的 Skill 摘要。
 */
public final class Skill {

    private final String name;
    private final String description;
    private final Path path;

    /**
     * 创建 Skill 摘要。
     */
    public Skill(String name, String description, Path path) {
        this.name = Objects.requireNonNull(name, "name");
        this.description = Objects.requireNonNull(description, "description");
        this.path = Objects.requireNonNull(path, "path");
    }

    /**
     * Skill 名称。
     */
    public String name() {
        return name;
    }

    /**
     * Skill 触发描述。
     */
    public String description() {
        return description;
    }

    /**
     * SKILL.md 路径。
     */
    public Path path() {
        return path;
    }
}
