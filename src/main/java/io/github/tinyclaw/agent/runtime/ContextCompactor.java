package io.github.tinyclaw.agent.runtime;

import io.github.tinyclaw.agent.domain.AgentContext;
import io.github.tinyclaw.agent.domain.SessionMessage;
import io.github.tinyclaw.agent.domain.SessionMessageKind;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 对 AgentContext 做上下文压缩，只处理 observation 内容。
 */
public final class ContextCompactor {

    private static final String OBSERVATION_TRUNCATED_TOKEN = "内容过长";
    private static final String OBSERVATION_MASKED_TOKEN = "早期工具输出已被压缩";
    private static final String READ_FILE_PREFIX = "[read_file path=";

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

        if (withinContextLimit(context) && !hasRepeatedReadFileObservation(context.observations())) {
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
                context.draftThought(),
                context.reviewFeedback(),
                context.approvedPlan(),
                compactWorkingMemory);
    }

    private boolean withinContextLimit(AgentContext context) {
        return estimatedTotalChars(context) <= policy.maxContextChars();
    }

    private int estimatedTotalChars(AgentContext context) {
        long total = 0L;
        total += lengthOf(context.goal());
        total += lengthOf(context.draftThought());
        total += lengthOf(context.reviewFeedback());
        total += lengthOf(context.approvedPlan());
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

        Map<String, Integer> latestReadByPath = latestReadFileObservationIndexes(source);
        List<String> compacted = new ArrayList<String>(source.size());
        boolean changed = false;

        for (int i = 0; i < source.size(); i++) {
            String observation = source.get(i);
            String path = readFilePath(observation);
            String compactedObservation = observation;
            if (path != null && !isErrorObservation(observation)
                    && latestReadByPath.getOrDefault(path, Integer.valueOf(i)).intValue() != i) {
                compactedObservation = "重复读取已压缩：" + path + "（原始长度：" + lengthOf(observation) + "）";
            } else {
                compactedObservation = compactObservation(observation);
            }
            if (compactedObservation == observation) {
                compacted.add(observation);
            } else {
                compacted.add(compactedObservation);
                changed = true;
            }
        }

        return changed ? List.copyOf(compacted) : source;
    }

    private boolean hasRepeatedReadFileObservation(List<String> observations) {
        Map<String, Integer> counts = new HashMap<String, Integer>();
        for (String observation : observations) {
            if (isErrorObservation(observation)) {
                continue;
            }
            String path = readFilePath(observation);
            if (path == null) {
                continue;
            }
            int count = counts.getOrDefault(path, 0) + 1;
            if (count > 1) {
                return true;
            }
            counts.put(path, count);
        }
        return false;
    }

    private Map<String, Integer> latestReadFileObservationIndexes(List<String> observations) {
        Map<String, Integer> latestByPath = new HashMap<String, Integer>();
        for (int i = 0; i < observations.size(); i++) {
            String observation = observations.get(i);
            if (isErrorObservation(observation)) {
                continue;
            }
            String path = readFilePath(observation);
            if (path != null) {
                latestByPath.put(path, Integer.valueOf(i));
            }
        }
        return latestByPath;
    }

    private String readFilePath(String observation) {
        if (observation == null || !observation.startsWith(READ_FILE_PREFIX)) {
            return null;
        }
        int end = observation.indexOf(']');
        if (end <= READ_FILE_PREFIX.length()) {
            return null;
        }
        String path = observation.substring(READ_FILE_PREFIX.length(), end).trim();
        return path.isEmpty() ? null : path;
    }

    private boolean isErrorObservation(String observation) {
        return observation != null && (observation.startsWith("Error executing ")
                || observation.contains("\n\nError executing "));
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
