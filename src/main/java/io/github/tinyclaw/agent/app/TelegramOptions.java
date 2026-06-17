package io.github.tinyclaw.agent.app;

final class TelegramOptions {

    private static final String DEBUG_OPTION = "--debug";

    private final boolean debug;

    private TelegramOptions(boolean debug) {
        this.debug = debug;
    }

    static TelegramOptions parse(String[] args) {
        boolean debug = false;
        for (int i = 1; i < args.length; i++) {
            if (DEBUG_OPTION.equals(args[i])) {
                debug = true;
            } else {
                throw new IllegalArgumentException("Unknown telegram option: " + args[i]);
            }
        }
        return new TelegramOptions(debug);
    }

    boolean debug() {
        return debug;
    }
}
