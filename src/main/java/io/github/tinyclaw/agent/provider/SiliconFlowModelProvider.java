package io.github.tinyclaw.agent.provider;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.tinyclaw.agent.domain.AgentContext;
import io.github.tinyclaw.agent.domain.Decision;
import io.github.tinyclaw.agent.domain.DecisionPhase;
import io.github.tinyclaw.agent.domain.FinishDecision;
import io.github.tinyclaw.agent.domain.ParallelToolDecision;
import io.github.tinyclaw.agent.domain.ThinkingDecision;
import io.github.tinyclaw.agent.domain.ToolCall;
import io.github.tinyclaw.agent.domain.ToolDecision;
import io.github.tinyclaw.agent.domain.ToolDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * SiliconFlow 模型提供方。
 * 使用 OpenAI 兼容 Chat Completions 协议驱动 Main Loop 决策。
 */
public final class SiliconFlowModelProvider implements ModelProvider {

    private static final Logger logger = LoggerFactory.getLogger(SiliconFlowModelProvider.class);
    private static final int MAX_DEBUG_TEXT_LENGTH = 240;

    private static final TypeReference<Map<String, Object>> ARGUMENTS_TYPE =
            new TypeReference<Map<String, Object>>() {
            };

    private final SiliconFlowConfig config;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Consumer<String> debugSink;

    /**
     * 创建不输出 provider debug 信息的 SiliconFlow provider。
     */
    public SiliconFlowModelProvider(SiliconFlowConfig config) {
        this(config, HttpClient.newHttpClient(), new ObjectMapper(), null);
    }

    /**
     * 创建可输出 provider 请求和响应摘要的 SiliconFlow provider。
     */
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

    /**
     * 根据当前上下文向 SiliconFlow 请求下一步决策。
     */
    @Override
    public Decision decide(AgentContext context, DecisionPhase phase, List<ToolDefinition> availableTools,
            String systemPrompt) {
        ObjectNode requestBody = buildRequestBody(context, phase, availableTools, systemPrompt);
        debugJson(phase, "Request JSON", requestBody);
        JsonNode response = send(requestBody);
        debugJson(phase, "Response JSON", response);
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
        debugDecision(phase, decision);
        return decision;
    }

    private ObjectNode buildRequestBody(AgentContext context, DecisionPhase phase,
            List<ToolDefinition> availableTools, String systemPrompt) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", config.model());
        root.put("stream", false);

        ArrayNode messages = root.putArray("messages");
        addMessage(messages, "system", systemPrompt);
        addMessage(messages, "user", context.goal());
        if (hasText(context.lastThought())) {
            addMessage(messages, "system", "内部思考记录：" + context.lastThought());
        }
        for (String observation : context.observations()) {
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
            logger.error("SiliconFlow request failed", ex);
            throw new RuntimeException("SiliconFlow request failed", ex);
        } catch (InterruptedException ex) {
            logger.error("SiliconFlow request interrupted", ex);
            Thread.currentThread().interrupt();
            throw new RuntimeException("SiliconFlow request interrupted", ex);
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            logger.error("SiliconFlow request failed with status {}: {}", response.statusCode(), response.body());
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
        JsonNode toolCalls = message.get("tool_calls");
        if (toolCalls == null || !toolCalls.isArray() || toolCalls.isEmpty()) {
            throw new RuntimeException("SiliconFlow tool calls missing or empty");
        }

        List<ToolCall> calls = new ArrayList<ToolCall>();
        for (JsonNode toolCall : toolCalls) {
            JsonNode function = toolCall.get("function");
            if (function == null || function.isNull()) {
                continue;
            }
            String name = text(function.get("name"));
            String argumentsText = text(function.get("arguments"));
            Map<String, Object> arguments = parseArguments(argumentsText);
            calls.add(new ToolCall(name, arguments));
        }

        if (calls.isEmpty()) {
            throw new RuntimeException("SiliconFlow tool calls missing function in all calls");
        }

        if (calls.size() == 1) {
            return new ToolDecision(calls.get(0));
        }
        return new ParallelToolDecision(calls);
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

    private void debugJson(DecisionPhase phase, String title, JsonNode node) {
        String pretty = prettyJson(toDebugJson(title, node));
        emitDebugBlock(phase, title, pretty);
    }

    private void debugDecision(DecisionPhase phase, Decision decision) {
        String summary = decisionSummary(decision);
        emitDebugBlock(phase, "Parsed Decision", summary);
    }

    private void emitDebugBlock(DecisionPhase phase, String title, String body) {
        if (debugSink == null) {
            return;
        }
        debugSink.accept("========== [Provider][" + phase + "] " + title + " ==========");
        debugSink.accept(body);
    }

    private String prettyJson(JsonNode node) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(node);
        } catch (JsonProcessingException ex) {
            return node.toString();
        }
    }

    private JsonNode toDebugJson(String title, JsonNode node) {
        if (node == null) {
            return null;
        }

        JsonNode copy = node.deepCopy();
        if ("Request JSON".equals(title) && copy instanceof ObjectNode) {
            summarizeRequestJson((ObjectNode) copy);
        }
        truncateLargeText(copy);
        return copy;
    }

    private void summarizeRequestJson(ObjectNode request) {
        JsonNode tools = request.get("tools");
        if (tools != null && tools.isArray()) {
            ObjectNode summary = request.putObject("tools_summary");
            summary.put("count", tools.size());
            ArrayNode names = summary.putArray("names");
            for (JsonNode tool : tools) {
                names.add(text(tool.path("function").path("name")));
            }
            request.remove("tools");
        }
    }

    private void truncateLargeText(JsonNode node) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node instanceof ObjectNode) {
            ObjectNode object = (ObjectNode) node;
            List<String> fieldNames = new ArrayList<String>();
            object.fieldNames().forEachRemaining(fieldNames::add);
            for (String fieldName : fieldNames) {
                JsonNode value = object.get(fieldName);
                if (value != null && value.isTextual()) {
                    object.put(fieldName, truncateText(value.asText("")));
                } else {
                    truncateLargeText(value);
                }
            }
            return;
        }
        if (node instanceof ArrayNode) {
            ArrayNode array = (ArrayNode) node;
            for (int index = 0; index < array.size(); index++) {
                JsonNode value = array.get(index);
                if (value != null && value.isTextual()) {
                    array.set(index, objectMapper.getNodeFactory().textNode(truncateText(value.asText(""))));
                } else {
                    truncateLargeText(value);
                }
            }
        }
    }

    private String truncateText(String value) {
        if (!hasText(value) || value.length() <= MAX_DEBUG_TEXT_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_DEBUG_TEXT_LENGTH)
                + "...(truncated " + (value.length() - MAX_DEBUG_TEXT_LENGTH) + " chars)";
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
        if (decision instanceof ParallelToolDecision) {
            return "ParallelToolDecision calls=" + ((ParallelToolDecision) decision).getCalls();
        }
        return decision.getClass().getSimpleName();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
