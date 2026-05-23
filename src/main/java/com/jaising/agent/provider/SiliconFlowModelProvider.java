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
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Collections;
import java.util.List;
import java.util.Map;

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

    public SiliconFlowModelProvider(SiliconFlowConfig config) {
        this(config, HttpClient.newHttpClient(), new ObjectMapper());
    }

    SiliconFlowModelProvider(SiliconFlowConfig config, HttpClient httpClient, ObjectMapper objectMapper) {
        this.config = config;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public Decision decide(AgentState state, DecisionPhase phase, List<ToolDefinition> availableTools) {
        ObjectNode requestBody = buildRequestBody(state, phase, availableTools);
        JsonNode response = send(requestBody);
        JsonNode message = firstMessage(response);

        if (phase == DecisionPhase.ACTION && hasToolCalls(message)) {
            return parseToolDecision(message);
        }

        String content = textOrFallback(message, "content", "reasoning_content");
        if (phase == DecisionPhase.THINKING) {
            return new ThinkingDecision(content);
        }
        return new FinishDecision(content);
    }

    private ObjectNode buildRequestBody(AgentState state, DecisionPhase phase, List<ToolDefinition> availableTools) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", config.model());
        root.put("stream", false);

        ArrayNode messages = root.putArray("messages");
        addMessage(messages, "system", "你是 java-tiny-claw 的模型 Provider，请根据当前任务给出下一步决策。");
        addMessage(messages, "user", state.goal());
        if (hasText(state.lastThought())) {
            addMessage(messages, "assistant", state.lastThought());
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
        try {
            return objectMapper.readValue(argumentsText, ARGUMENTS_TYPE);
        } catch (JsonProcessingException ex) {
            throw new RuntimeException("Invalid tool arguments JSON", ex);
        }
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

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
