package com.jaising.agent.runtime;

import com.jaising.agent.domain.AgentState;

import java.util.Objects;

public final class RunResult {

    private final AgentState state;

    public RunResult(AgentState state) {
        this.state = state;
    }

    public AgentState state() {
        return state;
    }

    public AgentState getState() {
        return state;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof RunResult that)) {
            return false;
        }
        return Objects.equals(state, that.state);
    }

    @Override
    public int hashCode() {
        return Objects.hash(state);
    }
}
