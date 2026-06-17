package io.github.tinyclaw.agent.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import io.github.tinyclaw.agent.domain.Decision;
import io.github.tinyclaw.agent.domain.FinishDecision;
import io.github.tinyclaw.agent.domain.ParallelToolDecision;
import io.github.tinyclaw.agent.domain.ThinkingDecision;
import io.github.tinyclaw.agent.domain.ToolCall;
import io.github.tinyclaw.agent.domain.ToolDecision;
import java.util.ArrayList;
import java.util.List;

final class ProviderDebugSummary {

    private ProviderDebugSummary() {
    }

    static String request(String model, JsonNode request) {
        JsonNode messages = request.path("messages");
        JsonNode tools = request.path("tools");
        return "model=" + model + "\n"
                + "messageCount=" + arraySize(messages) + "\n"
                + "toolCount=" + arraySize(tools) + "\n"
                + "toolNames=" + toolNames(tools) + "\n"
                + "maxTokens=" + maxTokens(request) + "\n"
                + "lastUserMessageLength=" + lastUserMessageLength(messages);
    }

    static String response(JsonNode response) {
        JsonNode choice = firstChoice(response);
        JsonNode message = choice.path("message");
        JsonNode usage = response.path("usage");
        return "finishReason=" + choice.path("finish_reason").asText("n/a") + "\n"
                + "contentLength=" + textLength(message.path("content").asText("")) + "\n"
                + "toolCallNames=" + toolCallNames(message.path("tool_calls")) + "\n"
                + "usageAvailable=" + usage.isObject() + "\n"
                + "promptTokens=" + usage.path("prompt_tokens").asInt(0) + "\n"
                + "completionTokens=" + usage.path("completion_tokens").asInt(0) + "\n"
                + "totalTokens=" + usage.path("total_tokens").asInt(0);
    }

    static String decision(Decision decision) {
        if (decision instanceof ThinkingDecision) {
            return "ThinkingDecision thoughtLength=" + textLength(((ThinkingDecision) decision).thought());
        }
        if (decision instanceof FinishDecision) {
            return "FinishDecision answerLength=" + textLength(((FinishDecision) decision).answer());
        }
        if (decision instanceof ToolDecision) {
            ToolCall call = ((ToolDecision) decision).call();
            return "ToolDecision tool=" + call.toolName() + " argumentKeys=" + argumentKeys(call);
        }
        if (decision instanceof ParallelToolDecision) {
            List<ToolCall> calls = ((ParallelToolDecision) decision).getCalls();
            return "ParallelToolDecision callCount=" + calls.size() + " toolNames=" + callToolNames(calls);
        }
        return decision.getClass().getSimpleName();
    }

    private static int arraySize(JsonNode node) {
        return node != null && node.isArray() ? node.size() : 0;
    }

    private static String maxTokens(JsonNode request) {
        JsonNode maxTokens = request.get("max_tokens");
        return maxTokens == null || maxTokens.isNull() ? "n/a" : maxTokens.asText();
    }

    private static int lastUserMessageLength(JsonNode messages) {
        if (messages == null || !messages.isArray()) {
            return 0;
        }
        for (int index = messages.size() - 1; index >= 0; index--) {
            JsonNode message = messages.get(index);
            if ("user".equals(message.path("role").asText())) {
                return textLength(message.path("content").asText(""));
            }
        }
        return 0;
    }

    private static JsonNode firstChoice(JsonNode response) {
        JsonNode choices = response.path("choices");
        if (choices.isArray() && !choices.isEmpty()) {
            return choices.get(0);
        }
        return MissingNode.getInstance();
    }

    private static List<String> toolNames(JsonNode tools) {
        List<String> names = new ArrayList<String>();
        if (tools == null || !tools.isArray()) {
            return names;
        }
        for (JsonNode tool : tools) {
            names.add(tool.path("function").path("name").asText(""));
        }
        return names;
    }

    private static List<String> toolCallNames(JsonNode toolCalls) {
        List<String> names = new ArrayList<String>();
        if (toolCalls == null || !toolCalls.isArray()) {
            return names;
        }
        for (JsonNode toolCall : toolCalls) {
            names.add(toolCall.path("function").path("name").asText(""));
        }
        return names;
    }

    private static List<String> callToolNames(List<ToolCall> calls) {
        List<String> names = new ArrayList<String>();
        for (ToolCall call : calls) {
            names.add(call.toolName());
        }
        return names;
    }

    private static List<String> argumentKeys(ToolCall call) {
        return new ArrayList<String>(call.arguments().keySet());
    }

    private static int textLength(String value) {
        return value == null ? 0 : value.length();
    }
}
