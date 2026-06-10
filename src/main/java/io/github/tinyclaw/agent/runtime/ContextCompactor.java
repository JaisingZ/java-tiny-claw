package io.github.tinyclaw.agent.runtime;

import io.github.tinyclaw.agent.domain.AgentContext;
import io.github.tinyclaw.agent.domain.SessionMessage;
import io.github.tinyclaw.agent.domain.SessionMessageKind;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 对 AgentContext 做上下文压缩，只处理 observation 内容。
 */
public final class ContextCompactor {

    private static final String OBSERVATION_TRUNCATED_TOKEN = "内容过长";
    private static final String OBSERVATION_MASKED_TOKEN = "早期工具输出已被压缩";

    private final ContextCompactionPolicy policy;

    /**
     * 使用默认压缩策略。
     */
    public ContextCompactor() {
        this(new ContextCompactionPolicy());
    }

    /**
     * 使用给定压缩策略。
     */
    public ContextCompactor(ContextCompactionPolicy policy) {
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    /**
     * 压缩上下文，如果上下文字符未超限则返回原始对象。
     */
    public AgentContext compact(AgentContext context) {
        Objects.requireNonNull(context, "context");

        if (withinContextLimit(context)) {
            return context;
        }

        List<SessionMessage> compactWorkingMemory = compactWorkingMemory(context.workingMemory());
        List<String> compactObservations = compactObservations(context.observations());

        if (compactWorkingMemory == context.workingMemory()
                && compactObservations == context.observations()) {
            return context;
        }

        return new AgentContext(
                context.task(),
                context.step(),
                compactObservations,
                context.lastThought(),
                compactWorkingMemory);
    }

    private boolean withinContextLimit(AgentContext context) {
        return estimatedTotalChars(context) <= policy.maxContextChars();
    }

    private int estimatedTotalChars(AgentContext context) {
        long total = 0L;
        total += lengthOf(context.goal());
        total += lengthOf(context.lastThought());
        for (SessionMessage message : context.workingMemory()) {
            total += lengthOf(message.content());
        }
        for (String observation : context.observations()) {
            total += lengthOf(observation);
        }
        return total > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) total;
    }

    private int lengthOf(String value) {
        return value == null ? 0 : value.length();
    }

    private List<SessionMessage> compactWorkingMemory(List<SessionMessage> source) {
        if (source.isEmpty()) {
            return source;
        }

        int totalMessages = source.size();
        int protectedStart = Math.max(0, totalMessages - policy.retainRecentMessages());
        boolean changed = false;
        List<SessionMessage> compacted = new ArrayList<SessionMessage>(source.size());

        for (int i = 0; i < source.size(); i++) {
            SessionMessage message = source.get(i);
            if (message.kind() != SessionMessageKind.OBSERVATION) {
                compacted.add(message);
                continue;
            }

            String compactedContent = shouldMaskObservation(i, protectedStart)
                    ? maskObservation(message.content())
                    : compactObservation(message.content());

            if (compactedContent == message.content()) {
                compacted.add(message);
            } else {
                compacted.add(new SessionMessage(message.kind(), compactedContent));
                changed = true;
            }
        }

        return changed ? Collections.unmodifiableList(compacted) : source;
    }

    private boolean shouldMaskObservation(int index, int protectedStart) {
        return index < protectedStart;
    }

    private List<String> compactObservations(List<String> source) {
        if (source.isEmpty()) {
            return source;
        }

        List<String> compacted = new ArrayList<String>(source.size());
        boolean changed = false;

        for (String observation : source) {
            String compactedObservation = compactObservation(observation);
            if (compactedObservation == observation) {
                compacted.add(observation);
            } else {
                compacted.add(compactedObservation);
                changed = true;
            }
        }

        return changed ? List.copyOf(compacted) : source;
    }

    private String compactObservation(String content) {
        if (content == null) {
            return "";
        }
        int len = content.length();
        if (len <= policy.maxObservationChars()) {
            return content;
        }
        String head = content.substring(0, policy.headChars());
        String tail = content.substring(len - policy.tailChars());
        return head
                + "\n\n"
                + OBSERVATION_TRUNCATED_TOKEN
                + "（原始长度：" + len + "）"
                + "\n\n"
                + tail;
    }

    private String maskObservation(String content) {
        if (content == null || content.isEmpty()) {
            return content == null ? "" : content;
        }
        if (content.length() <= policy.maskThresholdChars()) {
            return content;
        }
        return OBSERVATION_MASKED_TOKEN + "（原始长度：" + content.length() + "）";
    }
}
