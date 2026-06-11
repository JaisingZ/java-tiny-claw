package io.github.tinyclaw.agent.benchmark;

import java.util.Objects;

/**
 * 一个可自动判卷的 Agent benchmark 用例。
 */
public final class BenchmarkCase {

    private final String id;
    private final String name;
    private final String setupCommand;
    private final String taskPrompt;
    private final String validateCommand;
    private final int maxSteps;
    private final boolean enableThinking;

    public BenchmarkCase(String id, String name, String setupCommand, String taskPrompt, String validateCommand,
            int maxSteps, boolean enableThinking) {
        this.id = requireText(id, "id");
        this.name = requireText(name, "name");
        this.setupCommand = setupCommand == null ? "" : setupCommand;
        this.taskPrompt = requireText(taskPrompt, "taskPrompt");
        this.validateCommand = requireText(validateCommand, "validateCommand");
        if (maxSteps <= 0) {
            throw new IllegalArgumentException("maxSteps must be positive");
        }
        this.maxSteps = maxSteps;
        this.enableThinking = enableThinking;
    }

    public String id() {
        return id;
    }

    public String getId() {
        return id();
    }

    public String name() {
        return name;
    }

    public String getName() {
        return name();
    }

    public String setupCommand() {
        return setupCommand;
    }

    public String getSetupCommand() {
        return setupCommand();
    }

    public String taskPrompt() {
        return taskPrompt;
    }

    public String getTaskPrompt() {
        return taskPrompt();
    }

    public String validateCommand() {
        return validateCommand;
    }

    public String getValidateCommand() {
        return validateCommand();
    }

    public int maxSteps() {
        return maxSteps;
    }

    public int getMaxSteps() {
        return maxSteps();
    }

    public boolean enableThinking() {
        return enableThinking;
    }

    public boolean isEnableThinking() {
        return enableThinking();
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.trim().isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
