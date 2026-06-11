package io.github.tinyclaw.agent.benchmark;

/**
 * setup/validate 命令执行结果。
 */
public final class CommandResult {

    private final int exitCode;
    private final String output;

    public CommandResult(int exitCode, String output) {
        this.exitCode = exitCode;
        this.output = output == null ? "" : output;
    }

    public static CommandResult success(String output) {
        return new CommandResult(0, output);
    }

    public static CommandResult failed(int exitCode, String output) {
        return new CommandResult(exitCode, output);
    }

    public int exitCode() {
        return exitCode;
    }

    public int getExitCode() {
        return exitCode();
    }

    public String output() {
        return output;
    }

    public String getOutput() {
        return output();
    }

    public boolean success() {
        return exitCode == 0;
    }

    public boolean isSuccess() {
        return success();
    }
}
