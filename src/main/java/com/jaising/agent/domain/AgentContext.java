package com.jaising.agent.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * main loop 每轮执行上下文。
 * 保存当前任务、步数、观察信息和上一次思考内容。
 */
public final class AgentContext {

    private final Task task;
    private final int step;
    private final List<String> observations;
    private final String lastThought;

    /**
     * 创建上下文。
     * observations 会被封装为不可变列表，避免后续被外部修改。
     */
    public AgentContext(Task task, int step, List<String> observations, String lastThought) {
        this.task = task;
        this.step = step;
        List<String> safeObservations = observations == null
                ? Collections.<String>emptyList()
                : observations;
        this.observations = Collections.unmodifiableList(new ArrayList<String>(safeObservations));
        this.lastThought = lastThought;
    }

    /**
     * 根据任务创建主循环首轮上下文。
     */
    public static AgentContext create(Task task) {
        return new AgentContext(task, 0, Collections.<String>emptyList(), null);
    }

    /**
     * 进入下一步并返回新上下文。
     */
    public AgentContext advance() {
        return new AgentContext(task, step + 1, observations, lastThought);
    }

    /**
     * 添加一条观察记录并返回新上下文。
     */
    public AgentContext observe(String observation) {
        List<String> nextObservations = new ArrayList<String>(observations);
        nextObservations.add(observation);
        return new AgentContext(task, step, nextObservations, lastThought);
    }

    /**
     * 更新当前思考内容并返回新上下文。
     */
    public AgentContext think(String thought) {
        return new AgentContext(task, step, observations, thought);
    }

    /**
     * 获取任务实体。
     */
    public Task task() {
        return task;
    }

    /**
     * 获取任务Id。
     */
    public String taskId() {
        return task.taskId();
    }

    /**
     * 获取任务目标文本。
     */
    public String goal() {
        return task.goal();
    }

    /**
     * 获取当前步数。
     */
    public int step() {
        return step;
    }

    /**
     * 获取当前步数。
     */
    public int stepCount() {
        return step;
    }

    /**
     * 获取不可变观察列表。
     */
    public List<String> observations() {
        return observations;
    }

    /**
     * 获取最近一次思考内容。
     */
    public String lastThought() {
        return lastThought;
    }

    /**
     * 获取任务实体（兼容 get 风格调用）。
     */
    public Task getTask() {
        return task;
    }

    /**
     * 获取任务Id（兼容 get 风格调用）。
     */
    public String getTaskId() {
        return taskId();
    }

    /**
     * 获取任务目标文本（兼容 get 风格调用）。
     */
    public String getGoal() {
        return goal();
    }

    /**
     * 获取当前步数（兼容 get 风格调用）。
     */
    public int getStep() {
        return step;
    }

    /**
     * 获取当前步数（兼容 get 风格调用）。
     */
    public int getStepCount() {
        return step;
    }

    /**
     * 获取不可变观察列表（兼容 get 风格调用）。
     */
    public List<String> getObservations() {
        return observations;
    }

    /**
     * 获取最近一次思考内容（兼容 get 风格调用）。
     */
    public String getLastThought() {
        return lastThought;
    }

    /**
     * 判断上下文是否等价。
     */
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

    /**
     * 计算上下文哈希码。
     */
    @Override
    public int hashCode() {
        return Objects.hash(task, step, observations, lastThought);
    }
}
