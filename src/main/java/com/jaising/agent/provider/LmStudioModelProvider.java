package com.jaising.agent.provider;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jaising.agent.domain.AgentContext;
import com.jaising.agent.domain.Decision;
import com.jaising.agent.domain.DecisionPhase;
import com.jaising.agent.domain.FinishDecision;
import com.jaising.agent.domain.ParallelToolDecision;
import com.jaising.agent.domain.ThinkingDecision;
import com.jaising.agent.domain.ToolCall;
import com.jaising.agent.domain.ToolDecision;
import com.jaising.agent.domain.ToolDefinition;
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
 * LM Studio 模型提供方
 * 使用 OpenAI 兼容 Chat Completions 协议
 */
public final class LmStudioModelProvider implements ModelProvider {

    private static final Logger logger = LoggerFactory.getLogger(LmStudioModelProvider.class);
    private static final int MAX_DEBUG_TEXT_LENGTH = 240;
    private static final int THINKING_MAX_TOKENS = 256;
    private static final int ACTION_MAX_TOKENS = 1024;
    private static final int MAX_THINKING_TEXT_LENGTH = 400;

    private static final TypeReference<Map<String, Object>> ARGUMENTS_TYPE =
            new TypeReference<Map<String, Object>>() {
            };

    private final LmStudioConfig config;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Consumer<String> debugSink;

    public LmStudioModelProvider(LmStudioConfig config) {
        this(config, HttpClient.newHttpClient(), new ObjectMapper(), null);
    }

    public LmStudioModelProvider(LmStudioConfig config, Consumer<String> debugSink) {
        this(config, HttpClient.newHttpClient(), new ObjectMapper(), debugSink);
    }

    LmStudioModelProvider(LmStudioConfig config, HttpClient httpClient, ObjectMapper objectMapper) {
        this(config, httpClient, objectMapper, null);
    }

    LmStudioModelProvider(LmStudioConfig config, HttpClient httpClient, ObjectMapper objectMapper,
            Consumer<String> debugSink) {
        this.config = config;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.debugSink = debugSink;
    }

    @Override
    public Decision decide(AgentContext state, DecisionPhase phase, List<ToolDefinition> availableTools) {
        ObjectNode requestBody = buildRequestBody(state, phase, availableTools);
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
                decision = new ThinkingDecision(sanitizeThinkingText(content));
            } else {
                decision = new FinishDecision(sanitizeFinishText(content));
            }
        }
        debugDecision(phase, decision);
        return decision;
    }

    private ObjectNode buildRequestBody(AgentContext state, DecisionPhase phase, List<ToolDefinition> availableTools) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", config.model());
        root.put("stream", false);
        root.put("max_tokens", phase == DecisionPhase.THINKING ? THINKING_MAX_TOKENS : ACTION_MAX_TOKENS);

        ArrayNode messages = root.putArray("messages");
        addMessage(messages, "system", "你是 java-tiny-claw 的模型 Provider，请根据当前任务给出下一步决策。"
                + environmentInstruction()
                + phaseInstruction(phase));
        addMessage(messages, "user", state.goal());
        if (phase == DecisionPhase.THINKING && hasText(state.lastThought())) {
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
            return "当前是 THINKING 阶段：只输出内部计划，不要回答用户，不要调用工具。"
                    + "内部计划最多3条，每条一句话，总长度不要超过120个中文字符。"
                    + "内部计划必须基于已有 Observation，不能把已失败命令再次作为候选方案。";
        }
        return "当前是 ACTION 阶段：必须输出最终回答，或在需要时调用一个或多个独立工具；不要输出空内容。"
                + "最终回答必须直接面向用户，禁止输出思考过程，禁止输出分析，禁止输出解释，禁止输出计划，禁止输出推理，禁止输出英文说明。"
                + "不要复述用户要求、系统约束或 Observation 原文。"
                + "若用户要求一句话、一个标题、一个路径或一个简短结果，就只输出一句话或该结果本身。"
                + "如果多个操作互相独立（例如读取多个不同文件），建议在单轮中并行调用。"
                + "如果 Observation 已经满足用户目标且没有失败信息，直接输出最终回答，不要重复调用相同工具。"
                + "调用工具时 function.arguments 必须是完整闭合的严格 JSON object，不能使用 markdown、注释、自然语言包裹或尾随说明。"
                + "write_file 会自动创建父目录，创建文件前不要额外调用 mkdir。";
    }

    private String environmentInstruction() {
        return "运行环境事实：bash 工具在 Windows 下实际执行 powershell -NoProfile -NonInteractive -Command。"
                + "Do not use && or || in PowerShell commands. "
                + "Do not use ; to mean run the next command only when the previous command succeeds. "
                + "多步命令必须检查 $LASTEXITCODE。"
                + "创建或覆盖 Java 源码必须优先使用 write_file，源码必须是 UTF-8 文本，不能包含 UTF-16 或 NUL 字节。"
                + "不要用 PowerShell Set-Content 或 Out-File 写 Java 源码。"
                + "编译并运行 target/Hello.java 的推荐模板："
                + "javac target/Hello.java; if ($LASTEXITCODE -eq 0) { java -cp target Hello } else { exit $LASTEXITCODE }。";
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
            throw new RuntimeException("Failed to serialize LM Studio request", ex);
        }

        HttpRequest request = HttpRequest.newBuilder(endpoint())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException ex) {
            logger.error("LM Studio request failed", ex);
            throw new RuntimeException("LM Studio request failed", ex);
        } catch (InterruptedException ex) {
            logger.error("LM Studio request interrupted", ex);
            Thread.currentThread().interrupt();
            throw new RuntimeException("LM Studio request interrupted", ex);
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            logger.error("LM Studio request failed with status {}: {}", response.statusCode(), response.body());
            throw new RuntimeException("LM Studio request failed: " + response.statusCode()
                    + " " + response.body());
        }

        try {
            return objectMapper.readTree(response.body());
        } catch (JsonProcessingException ex) {
            throw new RuntimeException("Invalid LM Studio response JSON", ex);
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
            throw new RuntimeException("LM Studio returned empty choices");
        }
        JsonNode message = choices.get(0).get("message");
        if (message == null || message.isNull()) {
            throw new RuntimeException("LM Studio returned empty message");
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
            throw new RuntimeException("LM Studio tool calls missing or empty");
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
            throw new RuntimeException("LM Studio tool calls missing function in all calls");
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

    private String sanitizeThinkingText(String content) {
        String normalized = sanitizeFinishText(content);
        if (!hasText(normalized)) {
            return normalized;
        }
        if (normalized.length() <= MAX_THINKING_TEXT_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, MAX_THINKING_TEXT_LENGTH) + "...(truncated)";
    }

    private String sanitizeFinishText(String content) {
        if (content == null) {
            return "";
        }
        return content.trim();
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
