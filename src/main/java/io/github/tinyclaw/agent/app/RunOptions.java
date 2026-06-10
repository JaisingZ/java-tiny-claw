package io.github.tinyclaw.agent.app;

final class RunOptions {

    private static final String PROMPT_OPTION = "--prompt";
    private static final String PLAN_OPTION = "--plan";
    private static final String THINKING_OPTION = "--thinking";
    private static final String MAX_STEPS_OPTION = "--max-steps";
    private static final String DEBUG_OPTION = "--debug";
    private static final int DEFAULT_MAX_STEPS = 8;

    private final String prompt;
    private final boolean thinking;
    private final boolean planMode;
    private final int maxSteps;
    private final boolean debug;

    private RunOptions(String prompt, boolean thinking, boolean planMode, int maxSteps, boolean debug) {
        this.prompt = prompt;
        this.thinking = thinking;
        this.planMode = planMode;
        this.maxSteps = maxSteps;
        this.debug = debug;
    }

    static RunOptions parse(String[] args) {
        String prompt = null;
        boolean thinking = false;
        boolean planMode = false;
        int maxSteps = DEFAULT_MAX_STEPS;
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
            } else if (MAX_STEPS_OPTION.equals(args[i])) {
                i++;
                if (i >= args.length) {
                    throw new IllegalArgumentException("Missing value for --max-steps");
                }
                maxSteps = parseMaxSteps(args[i]);
            } else if (DEBUG_OPTION.equals(args[i])) {
                debug = true;
            } else {
                throw new IllegalArgumentException("Unknown run option: " + args[i]);
            }
        }
        if (!hasText(prompt)) {
            throw new IllegalArgumentException("--prompt is required");
        }
        return new RunOptions(prompt, thinking, planMode, maxSteps, debug);
    }

    private static int parseMaxSteps(String value) {
        try {
            int parsed = Integer.parseInt(value);
            if (parsed <= 0) {
                throw new IllegalArgumentException("--max-steps must be positive");
            }
            return parsed;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid --max-steps: " + value, ex);
        }
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

    int maxSteps() {
        return maxSteps;
    }

    boolean debug() {
        return debug;
    }
}
