package io.github.tinyclaw.agent.runtime;

/**
 * Provider 调用失败，reason 会直接进入 RunResult。
 */
final class ProviderCallException extends RuntimeException {

    private final String reason;

    ProviderCallException(String reason) {
        super(reason);
        this.reason = reason;
    }

    String reason() {
        return reason;
    }
}
