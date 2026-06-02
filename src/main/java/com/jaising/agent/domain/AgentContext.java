package com.jaising.agent.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Agent 当前轮上下文。
 * 只承载模型和工具需要读取的短期信息。
 */
public final class AgentContext {

    private final Task task;
    private final int step;
    private final List<String> observations;
    private final String lastThought;

    public AgentContext(Task task, int step, List<String> observations, String lastThought) {
        this.task = task;
        this.step = step;
        List<String> safeObservations = observations == null
                ? Collections.<String>emptyList()
                : observations;
        this.observations = Collections.unmodifiableList(new ArrayList<String>(safeObservations));
        this.lastThought = lastThought;
    }

    public static AgentContext create(Task task) {
        return new AgentContext(task, 0, Collections.<String>emptyList(), null);
    }

    public AgentContext advance() {
        return new AgentContext(task, step + 1, observations, lastThought);
    }

    public AgentContext observe(String observation) {
        List<String> nextObservations = new ArrayList<String>(observations);
        nextObservations.add(observation);
        return new AgentContext(task, step, nextObservations, lastThought);
    }

    public AgentContext think(String thought) {
        return new AgentContext(task, step, observations, thought);
    }

    public Task task() {
        return task;
    }

    public String taskId() {
        return task.taskId();
    }

    public String goal() {
        return task.goal();
    }

    public int step() {
        return step;
    }

    public int stepCount() {
        return step;
    }

    public List<String> observations() {
        return observations;
    }

    public String lastThought() {
        return lastThought;
    }

    public Task getTask() {
        return task;
    }

    public String getTaskId() {
        return taskId();
    }

    public String getGoal() {
        return goal();
    }

    public int getStep() {
        return step;
    }

    public int getStepCount() {
        return step;
    }

    public List<String> getObservations() {
        return observations;
    }

    public String getLastThought() {
        return lastThought;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof AgentContext)) {
            return false;
        }
        AgentContext that = (AgentContext) other;
        return step == that.step
                && Objects.equals(task, that.task)
                && Objects.equals(observations, that.observations)
                && Objects.equals(lastThought, that.lastThought);
    }

    @Override
    public int hashCode() {
        return Objects.hash(task, step, observations, lastThought);
    }
}
