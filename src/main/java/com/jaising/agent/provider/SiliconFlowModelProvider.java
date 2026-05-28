package com.jaising.agent.provider;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jaising.agent.domain.AgentState;
import com.jaising.agent.domain.Decision;
import com.jaising.agent.domain.DecisionPhase;
import com.jaising.agent.domain.FinishDecision;
import com.jaising.agent.domain.ThinkingDecision;
import com.jaising.agent.domain.ToolCall;
import com.jaising.agent.domain.ToolDecision;
import com.jaising.agent.domain.ToolDefinition;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * SiliconFlow 模型提供方
 * 使用 OpenAI 兼容 Chat Completions 协议
 */
public final class SiliconFlowModelProvider implements ModelProvider {

    private static final TypeReference<Map<String, Object>> ARGUMENTS_TYPE =
            new TypeReference<Map<String, Object>>() {
            };

    private final SiliconFlowConfig config;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Consumer<String> debugSink;

    public SiliconFlowModelProvider(SiliconFlowConfig config) {
        this(config, HttpClient.newHttpClient(), new ObjectMapper(), null);
    }

    public SiliconFlowModelProvider(SiliconFlowConfig config, PrintStream debugOutput) {
        this(config, HttpClient.newHttpClient(), new ObjectMapper(), debugOutput::println);
    }

    public SiliconFlowModelProvider(SiliconFlowConfig config, Consumer<String> debugSink) {
        this(config, HttpClient.newHttpClient(), new ObjectMapper(), debugSink);
    }

    SiliconFlowModelProvider(SiliconFlowConfig config, HttpClient httpClient, ObjectMapper objectMapper) {
        this(config, httpClient, objectMapper, null);
    }

    SiliconFlowModelProvider(SiliconFlowConfig config, HttpClient httpClient, ObjectMapper objectMapper,
            Consumer<String> debugSink) {
        this.config = config;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.debugSink = debugSink;
    }

    @Override
    public Decision decide(AgentState state, DecisionPhase phase, List<ToolDefinition> availableTools) {
        ObjectNode requestBody = buildRequestBody(state, phase, availableTools);
        debug("========== [Provider][" + phase + "] Request JSON ==========");
        debugJson(requestBody);
        JsonNode response = send(requestBody);
        debug("========== [Provider][" + phase + "] Response JSON ==========");
        debugJson(response);
        JsonNode message = firstMessage(response);

        Decision decision;
        if (phase == DecisionPhase.ACTION && hasToolCalls(message)) {
            decision = parseToolDecision(message);
        } else {
            String content = textOrFallback(message, "content", "reasoning_content");
            if (phase == DecisionPhase.THINKING) {
                decision = new ThinkingDecision(content);
            } else {
                decision = new FinishDecision(content);
            }
        }

        debug("========== [Provider][" + phase + "] Parsed Decision ==========");
        debug(decisionSummary(decision));
        return decision;
    }

    private ObjectNode buildRequestBody(AgentState state, DecisionPhase phase, List<ToolDefinition> availableTools) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", config.model());
        root.put("stream", false);

        ArrayNode messages = root.putArray("messages");
        addMessage(messages, "system", "你是 java-tiny-claw 的模型 Provider，请根据当前任务给出下一步决策。"
                + phaseInstruction(phase));
        addMessage(messages, "user", state.goal());
        if (hasText(state.lastThought())) {
            addMessage(messages, "system", "内部思考记录：" + state.lastThought());
        }
        for (String observation : state.observations()) {
            addMessage(messages, "user", "Observation: " + observation);
        }

        if (phase == DecisionPhase.ACTION && availableTools != null && !availableTools.isEmpty()) {
            ArrayNode tools = root.putArray("tools");
            for (ToolDefinition definition : availableTools) {
                ObjectNode tool = tools.addObject();
                tool.put("type", "function");
                ObjectNode function = tool.putObject("function");
                function.put("name", definition.name());
                function.put("description", definition.description());
                function.set("parameters", objectMapper.valueToTree(definition.parameters()));
            }
        }

        return root;
    }

    private String phaseInstruction(DecisionPhase phase) {
        if (phase == DecisionPhase.THINKING) {
            return "当前是 THINKING 阶段：只输出内部计划，不要回答用户，不要调用工具。";
        }
        return "当前是 ACTION 阶段：必须输出最终回答，或在需要时调用一个工具；不要输出空内容。"
                + "调用工具时 function.arguments 必须是完整闭合的严格 JSON object，不能使用 markdown、注释、自然语言包裹或尾随说明。"
                + "write_file 会自动创建父目录，创建文件前不要额外调用 mkdir。";
    }

    private void addMessage(ArrayNode messages, String role, String content) {
        ObjectNode message = messages.addObject();
        message.put("role", role);
        message.put("content", content == null ? "" : content);
    }

    private JsonNode send(ObjectNode requestBody) {
        String body;
        try {
            body = objectMapper.writeValueAsString(requestBody);
        } catch (JsonProcessingException ex) {
            throw new RuntimeException("Failed to serialize SiliconFlow request", ex);
        }

        HttpRequest request = HttpRequest.newBuilder(endpoint())
                .header("Authorization", "Bearer " + config.apiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException ex) {
            throw new RuntimeException("SiliconFlow request failed", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("SiliconFlow request interrupted", ex);
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new RuntimeException("SiliconFlow request failed: " + response.statusCode()
                    + " " + response.body());
        }

        try {
            return objectMapper.readTree(response.body());
        } catch (JsonProcessingException ex) {
            throw new RuntimeException("Invalid SiliconFlow response JSON", ex);
        }
    }

    private URI endpoint() {
        String baseUrl = config.baseUrl();
        String trimmed = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return URI.create(trimmed + "/chat/completions");
    }

    private JsonNode firstMessage(JsonNode response) {
        JsonNode choices = response.get("choices");
        if (choices == null || !choices.isArray() || choices.isEmpty()) {
            throw new RuntimeException("SiliconFlow returned empty choices");
        }
        JsonNode message = choices.get(0).get("message");
        if (message == null || message.isNull()) {
            throw new RuntimeException("SiliconFlow returned empty message");
        }
        return message;
    }

    private boolean hasToolCalls(JsonNode message) {
        JsonNode toolCalls = message.get("tool_calls");
        return toolCalls != null && toolCalls.isArray() && !toolCalls.isEmpty();
    }

    private Decision parseToolDecision(JsonNode message) {
        JsonNode toolCall = message.get("tool_calls").get(0);
        JsonNode function = toolCall.get("function");
        if (function == null || function.isNull()) {
            throw new RuntimeException("SiliconFlow tool call missing function");
        }
        String name = text(function.get("name"));
        String argumentsText = text(function.get("arguments"));
        Map<String, Object> arguments = parseArguments(argumentsText);
        return new ToolDecision(new ToolCall(name, arguments));
    }

    private Map<String, Object> parseArguments(String argumentsText) {
        if (!hasText(argumentsText)) {
            return Collections.emptyMap();
        }

        Set<String> candidates = argumentCandidates(argumentsText);
        for (String candidate : candidates) {
            Map<String, Object> parsed = tryParseArguments(candidate);
            if (parsed != null) {
                return parsed;
            }
        }
        throw new RuntimeException("Invalid tool arguments JSON: " + argumentsText);
    }

    private Set<String> argumentCandidates(String argumentsText) {
        Set<String> candidates = new LinkedHashSet<String>();
        addArgumentCandidate(candidates, argumentsText);

        String stripped = stripMarkdownFence(argumentsText);
        addArgumentCandidate(candidates, stripped);
        addArgumentCandidate(candidates, extractJsonObject(stripped));

        String unescaped = unescapeJsonString(argumentsText);
        addArgumentCandidate(candidates, unescaped);
        addArgumentCandidate(candidates, stripMarkdownFence(unescaped));
        addArgumentCandidate(candidates, extractJsonObject(unescaped));
        addArgumentCandidate(candidates, extractJsonObject(stripMarkdownFence(unescaped)));

        addArgumentCandidate(candidates, extractJsonObject(argumentsText));
        return candidates;
    }

    private void addArgumentCandidate(Set<String> candidates, String value) {
        if (hasText(value)) {
            String trimmed = value.trim();
            candidates.add(trimmed);
            String completed = completeTrailingJsonClosers(trimmed);
            if (hasText(completed)) {
                candidates.add(completed);
            }
        }
    }

    private Map<String, Object> tryParseArguments(String candidate) {
        try {
            JsonNode node = objectMapper.readTree(candidate);
            if (node == null || !node.isObject()) {
                return null;
            }
            return objectMapper.convertValue(node, ARGUMENTS_TYPE);
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    private String stripMarkdownFence(String value) {
        if (!hasText(value)) {
            return value;
        }
        String trimmed = value.trim();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }
        int firstLineEnd = trimmed.indexOf('\n');
        int lastFence = trimmed.lastIndexOf("```");
        if (firstLineEnd < 0 || lastFence <= firstLineEnd) {
            return trimmed;
        }
        return trimmed.substring(firstLineEnd + 1, lastFence).trim();
    }

    private String extractJsonObject(String value) {
        if (!hasText(value)) {
            return value;
        }
        String trimmed = value.trim();
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return trimmed;
        }
        return trimmed.substring(start, end + 1).trim();
    }

    private String unescapeJsonString(String value) {
        if (!hasText(value)) {
            return value;
        }
        String trimmed = value.trim();
        if (!trimmed.startsWith("\"") || !trimmed.endsWith("\"")) {
            return trimmed;
        }
        try {
            return objectMapper.readValue(trimmed, String.class);
        } catch (JsonProcessingException ex) {
            return trimmed;
        }
    }

    private String completeTrailingJsonClosers(String value) {
        if (!hasText(value)) {
            return value;
        }
        String trimmed = value.trim();
        if (!trimmed.startsWith("{")) {
            return null;
        }

        Deque<Character> closers = new ArrayDeque<Character>();
        boolean inString = false;
        boolean escaped = false;
        for (int index = 0; index < trimmed.length(); index++) {
            char current = trimmed.charAt(index);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (inString) {
                if (current == '\\') {
                    escaped = true;
                } else if (current == '"') {
                    inString = false;
                }
                continue;
            }
            if (current == '"') {
                inString = true;
            } else if (current == '{') {
                closers.push('}');
            } else if (current == '[') {
                closers.push(']');
            } else if (current == '}' || current == ']') {
                if (closers.isEmpty() || closers.pop() != current) {
                    return null;
                }
            }
        }

        if (inString || closers.isEmpty() || closers.size() > 8) {
            return null;
        }

        StringBuilder completed = new StringBuilder(trimmed);
        while (!closers.isEmpty()) {
            completed.append(closers.pop());
        }
        return completed.toString();
    }

    private String textOrFallback(JsonNode node, String primary, String fallback) {
        String primaryText = text(node.get(primary));
        if (hasText(primaryText)) {
            return primaryText;
        }
        return text(node.get(fallback));
    }

    private String text(JsonNode node) {
        if (node == null || node.isNull()) {
            return "";
        }
        return node.asText("");
    }

    private void debugJson(JsonNode node) {
        if (debugSink == null) {
            return;
        }
        try {
            debugSink.accept(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(node));
        } catch (JsonProcessingException ex) {
            debugSink.accept(node.toString());
        }
    }

    private void debug(String message) {
        if (debugSink != null) {
            debugSink.accept(message);
        }
    }

    private String decisionSummary(Decision decision) {
        if (decision instanceof ThinkingDecision) {
            return "ThinkingDecision thought=" + ((ThinkingDecision) decision).thought();
        }
        if (decision instanceof FinishDecision) {
            return "FinishDecision answer=" + ((FinishDecision) decision).answer();
        }
        if (decision instanceof ToolDecision) {
            ToolCall call = ((ToolDecision) decision).call();
            return "ToolDecision tool=" + call.toolName() + " args=" + call.arguments();
        }
        return decision.getClass().getSimpleName();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
