package io.github.tinyclaw.agent.app;

final class RunOptions {

    private static final String PROMPT_OPTION = "--prompt";
    private static final String PLAN_OPTION = "--plan";
    private static final String THINKING_OPTION = "--thinking";
    private static final String DEBUG_OPTION = "--debug";

    private final String prompt;
    private final boolean thinking;
    private final boolean planMode;
    private final boolean debug;

    private RunOptions(String prompt, boolean thinking, boolean planMode, boolean debug) {
        this.prompt = prompt;
        this.thinking = thinking;
        this.planMode = planMode;
        this.debug = debug;
    }

    static RunOptions parse(String[] args) {
        String prompt = null;
        boolean thinking = false;
        boolean planMode = false;
        boolean debug = false;
        for (int i = 1; i < args.length; i++) {
            if (PROMPT_OPTION.equals(args[i])) {
                i++;
                if (i >= args.length) {
                    throw new IllegalArgumentException("Missing value for --prompt");
                }
                prompt = args[i];
            } else if (PLAN_OPTION.equals(args[i])) {
                planMode = true;
            } else if (THINKING_OPTION.equals(args[i])) {
                thinking = true;
            } else if (DEBUG_OPTION.equals(args[i])) {
                debug = true;
            } else {
                throw new IllegalArgumentException("Unknown run option: " + args[i]);
            }
        }
        if (!hasText(prompt)) {
            throw new IllegalArgumentException("--prompt is required");
        }
        return new RunOptions(prompt, thinking, planMode, debug);
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    String prompt() {
        return prompt;
    }

    boolean thinking() {
        return thinking;
    }

    boolean planMode() {
        return planMode;
    }

    boolean debug() {
        return debug;
    }
}
