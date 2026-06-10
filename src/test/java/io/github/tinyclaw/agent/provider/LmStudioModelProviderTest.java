package io.github.tinyclaw.agent.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.tinyclaw.agent.domain.AgentContext;
import io.github.tinyclaw.agent.domain.Decision;
import io.github.tinyclaw.agent.domain.DecisionPhase;
import io.github.tinyclaw.agent.domain.FinishDecision;
import io.github.tinyclaw.agent.domain.SessionMessage;
import io.github.tinyclaw.agent.domain.Task;
import io.github.tinyclaw.agent.domain.ThinkingDecision;
import io.github.tinyclaw.agent.domain.ToolCall;
import io.github.tinyclaw.agent.domain.ToolDecision;
import io.github.tinyclaw.agent.domain.ToolDefinition;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class LmStudioModelProviderTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String SYSTEM_PROMPT = "external system prompt";

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void parsesActionTextResponseAsFinishDecisionWithoutAuthorizationHeader() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<String>();
        AtomicReference<JsonNode> requestBody = new AtomicReference<JsonNode>();
        startServer(200, completionWithMessage("{\"content\":\"done\"}"), authorization, requestBody);

        LmStudioModelProvider provider = new LmStudioModelProvider(
                new LmStudioConfig(baseUrl(), "qwen-local"));

        Decision decision = provider.decide(AgentContext.create(new Task("task-1", "finish it")),
                DecisionPhase.ACTION, Collections.<ToolDefinition>emptyList(), SYSTEM_PROMPT);

        assertThat(decision).isEqualTo(new FinishDecision("done"));
        assertThat(authorization.get()).isNull();
        assertThat(requestBody.get().get("stream").asBoolean()).isFalse();
        assertThat(requestBody.get().get("model").asText()).isEqualTo("qwen-local");
        assertThat(requestBody.get().has("tools")).isFalse();
        assertSystemPromptContains(requestBody.get(), SYSTEM_PROMPT);
    }

    @Test
    void thinkingPhaseDoesNotSendToolsAndUsesReasoningContentFallback() throws Exception {
        AtomicReference<JsonNode> requestBody = new AtomicReference<JsonNode>();
        startServer(200, completionWithMessage("{\"content\":\"\",\"reasoning_content\":\"think first\"}"),
                new AtomicReference<String>(), requestBody);
        LmStudioModelProvider provider = new LmStudioModelProvider(
                new LmStudioConfig(baseUrl(), "qwen-local"));
        ToolDefinition tool = new ToolDefinition("echo", "echo",
                Collections.<String, Object>singletonMap("type", "object"));

        Decision decision = provider.decide(AgentContext.create(new Task("task-2", "think")),
                DecisionPhase.THINKING, Collections.singletonList(tool), SYSTEM_PROMPT);

        assertThat(decision).isEqualTo(new ThinkingDecision("think first"));
        assertThat(requestBody.get().has("tools")).isFalse();
        assertThat(requestBody.get().get("max_tokens").asInt()).isEqualTo(256);
        assertSystemPromptContains(requestBody.get(), SYSTEM_PROMPT);
    }

    @Test
    void actionPhaseRejectsReasoningOnlyResponseAsFinalAnswer() throws Exception {
        startServer(200, completionWithMessage("{\"content\":\"\",\"reasoning_content\":\"internal reasoning\"}",
                        "length"),
                new AtomicReference<String>(), new AtomicReference<JsonNode>());
        LmStudioModelProvider provider = new LmStudioModelProvider(
                new LmStudioConfig(baseUrl(), "qwen-local"));

        assertThatThrownBy(() -> provider.decide(AgentContext.create(new Task("task-reasoning-only", "finish")),
                DecisionPhase.ACTION, Collections.<ToolDefinition>emptyList(), SYSTEM_PROMPT))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("LM Studio action response missing content or tool calls");
    }

    @Test
    void actionPhaseSendsToolsAndParsesFirstToolCall() throws Exception {
        AtomicReference<JsonNode> requestBody = new AtomicReference<JsonNode>();
        startServer(200, completionWithMessage("{\"tool_calls\":[{\"id\":\"call-1\",\"type\":\"function\"," 
                        + "\"function\":{\"name\":\"echo\",\"arguments\":\"{\\\"text\\\":\\\"hello\\\"}\"}}]}"),
                new AtomicReference<String>(), requestBody);
        LmStudioModelProvider provider = new LmStudioModelProvider(
                new LmStudioConfig(baseUrl(), "qwen-local"));
        ToolDefinition tool = new ToolDefinition("echo", "回显文本",
                Collections.<String, Object>singletonMap("type", "object"));

        Decision decision = provider.decide(AgentContext.create(new Task("task-3", "echo hello")),
                DecisionPhase.ACTION, Collections.singletonList(tool), SYSTEM_PROMPT);

        assertThat(requestBody.get().get("max_tokens").asInt()).isEqualTo(1024);
        assertThat(requestBody.get().get("tools")).hasSize(1);
        assertThat(requestBody.get().get("tools").get(0).get("type").asText()).isEqualTo("function");
        assertThat(requestBody.get().get("tools").get(0).get("function").get("name").asText()).isEqualTo("echo");
        assertSystemPromptContains(requestBody.get(), SYSTEM_PROMPT);
        assertThat(decision).isEqualTo(new ToolDecision(new ToolCall("echo",
                Collections.<String, Object>singletonMap("text", "hello"))));
    }

    @Test
    void actionPhaseDoesNotSendLastThoughtBackToModel() throws Exception {
        AtomicReference<JsonNode> requestBody = new AtomicReference<JsonNode>();
        startServer(200, completionWithMessage("{\"content\":\"done\"}"),
                new AtomicReference<String>(), requestBody);
        LmStudioModelProvider provider = new LmStudioModelProvider(
                new LmStudioConfig(baseUrl(), "qwen-local"));

        AgentContext state = AgentContext.create(new Task("task-action-no-last-thought", "finish"))
                .think("这是一段不该回传给 ACTION 阶段的内部思考");

        Decision decision = provider.decide(state, DecisionPhase.ACTION,
                Collections.<ToolDefinition>emptyList(), SYSTEM_PROMPT);

        assertThat(decision).isEqualTo(new FinishDecision("done"));
        assertThat(requestBody.get().get("messages")).hasSize(2);
        assertThat(requestBody.get().toString()).doesNotContain("内部思考记录");
        assertThat(requestBody.get().toString()).doesNotContain("不该回传");
    }

    @Test
    void sendsWorkingMemoryBeforeCurrentTaskAndObservations() throws Exception {
        AtomicReference<JsonNode> requestBody = new AtomicReference<JsonNode>();
        startServer(200, completionWithMessage("{\"content\":\"done\"}"),
                new AtomicReference<String>(), requestBody);
        LmStudioModelProvider provider = new LmStudioModelProvider(
                new LmStudioConfig(baseUrl(), "qwen-local"));
        AgentContext context = AgentContext.create(new Task("task-memory", "current question"),
                Arrays.asList(
                        SessionMessage.user("previous question"),
                        SessionMessage.assistant("previous answer"),
                        SessionMessage.observation("previous observation")))
                .observe("current observation");

        Decision decision = provider.decide(context, DecisionPhase.ACTION,
                Collections.<ToolDefinition>emptyList(), SYSTEM_PROMPT);

        assertThat(decision).isEqualTo(new FinishDecision("done"));
        JsonNode messages = requestBody.get().get("messages");
        assertThat(messages).hasSize(6);
        assertThat(messages.get(0).get("role").asText()).isEqualTo("system");
        assertThat(messages.get(1).get("role").asText()).isEqualTo("user");
        assertThat(messages.get(1).get("content").asText()).isEqualTo("previous question");
        assertThat(messages.get(2).get("role").asText()).isEqualTo("assistant");
        assertThat(messages.get(2).get("content").asText()).isEqualTo("previous answer");
        assertThat(messages.get(3).get("role").asText()).isEqualTo("user");
        assertThat(messages.get(3).get("content").asText()).isEqualTo("Observation: previous observation");
        assertThat(messages.get(4).get("role").asText()).isEqualTo("user");
        assertThat(messages.get(4).get("content").asText()).isEqualTo("current question");
        assertThat(messages.get(5).get("role").asText()).isEqualTo("user");
        assertThat(messages.get(5).get("content").asText()).isEqualTo("Observation: current observation");
    }

    @Test
    void parsesMarkdownFencedToolArguments() throws Exception {
        startServer(200, completionWithToolArguments("```json\n{\"text\":\"hello\"}\n```"),
                new AtomicReference<String>(), new AtomicReference<JsonNode>());
        LmStudioModelProvider provider = new LmStudioModelProvider(
                new LmStudioConfig(baseUrl(), "qwen-local"));

        Decision decision = provider.decide(AgentContext.create(new Task("task-fenced", "echo hello")),
                DecisionPhase.ACTION, Collections.<ToolDefinition>singletonList(new ToolDefinition(
                        "echo", "echo", Collections.<String, Object>singletonMap("type", "object"))),
                SYSTEM_PROMPT);

        assertThat(decision).isEqualTo(new ToolDecision(new ToolCall("echo",
                Collections.<String, Object>singletonMap("text", "hello"))));
    }

    @Test
    void parsesDoubleEscapedToolArguments() throws Exception {
        startServer(200, completionWithToolArguments("\"{\\\"text\\\":\\\"hello\\\"}\""),
                new AtomicReference<String>(), new AtomicReference<JsonNode>());
        LmStudioModelProvider provider = new LmStudioModelProvider(
                new LmStudioConfig(baseUrl(), "qwen-local"));

        Decision decision = provider.decide(AgentContext.create(new Task("task-escaped", "echo hello")),
                DecisionPhase.ACTION, Collections.<ToolDefinition>singletonList(new ToolDefinition(
                        "echo", "echo", Collections.<String, Object>singletonMap("type", "object"))),
                SYSTEM_PROMPT);

        assertThat(decision).isEqualTo(new ToolDecision(new ToolCall("echo",
                Collections.<String, Object>singletonMap("text", "hello"))));
    }

    @Test
    void parsesToolArgumentsWrappedInText() throws Exception {
        startServer(200, completionWithToolArguments("arguments are {\"text\":\"hello\"}"),
                new AtomicReference<String>(), new AtomicReference<JsonNode>());
        LmStudioModelProvider provider = new LmStudioModelProvider(
                new LmStudioConfig(baseUrl(), "qwen-local"));

        Decision decision = provider.decide(AgentContext.create(new Task("task-wrapped", "echo hello")),
                DecisionPhase.ACTION, Collections.<ToolDefinition>singletonList(new ToolDefinition(
                        "echo", "echo", Collections.<String, Object>singletonMap("type", "object"))),
                SYSTEM_PROMPT);

        assertThat(decision).isEqualTo(new ToolDecision(new ToolCall("echo",
                Collections.<String, Object>singletonMap("text", "hello"))));
    }

    @Test
    void parsesToolArgumentsMissingTrailingObjectBrace() throws Exception {
        startServer(200, completionWithToolArguments("{\"text\":\"hello\""),
                new AtomicReference<String>(), new AtomicReference<JsonNode>());
        LmStudioModelProvider provider = new LmStudioModelProvider(
                new LmStudioConfig(baseUrl(), "qwen-local"));

        Decision decision = provider.decide(AgentContext.create(new Task("task-missing-brace", "echo hello")),
                DecisionPhase.ACTION, Collections.<ToolDefinition>singletonList(new ToolDefinition(
                        "echo", "echo", Collections.<String, Object>singletonMap("type", "object"))),
                SYSTEM_PROMPT);

        assertThat(decision).isEqualTo(new ToolDecision(new ToolCall("echo",
                Collections.<String, Object>singletonMap("text", "hello"))));
    }

    @Test
    void parsesToolArgumentsMissingTrailingBraceWhenTextContainsBraces() throws Exception {
        startServer(200, completionWithToolArguments("{\"text\":\"literal { and } braces\""),
                new AtomicReference<String>(), new AtomicReference<JsonNode>());
        LmStudioModelProvider provider = new LmStudioModelProvider(
                new LmStudioConfig(baseUrl(), "qwen-local"));

        Decision decision = provider.decide(AgentContext.create(new Task("task-string-braces", "echo braces")),
                DecisionPhase.ACTION, Collections.<ToolDefinition>singletonList(new ToolDefinition(
                        "echo", "echo", Collections.<String, Object>singletonMap("type", "object"))),
                SYSTEM_PROMPT);

        assertThat(decision).isEqualTo(new ToolDecision(new ToolCall("echo",
                Collections.<String, Object>singletonMap("text", "literal { and } braces"))));
    }

    @Test
    void debugOutputPrintsRequestResponsePhaseAndDecision() throws Exception {
        AtomicReference<JsonNode> requestBody = new AtomicReference<JsonNode>();
        startServer(200, completionWithMessage("{\"content\":\"done\"}"),
                new AtomicReference<String>(), requestBody);
        StringBuilder debugOutput = new StringBuilder();
        LmStudioModelProvider provider = new LmStudioModelProvider(
                new LmStudioConfig(baseUrl(), "qwen-local"),
                line -> debugOutput.append(line).append('\n'));

        provider.decide(AgentContext.create(new Task("task-debug", "debug it")),
                DecisionPhase.ACTION, Collections.<ToolDefinition>emptyList(), SYSTEM_PROMPT);

        assertThat(debugOutput.toString())
                .contains("========== [Provider][ACTION] Request JSON ==========")
                .contains("\"model\" : \"qwen-local\"")
                .contains("\"content\" : \"debug it\"")
                .contains("========== [Provider][ACTION] Response JSON ==========")
                .contains("\"content\" : \"done\"")
                .contains("========== [Provider][ACTION] Parsed Decision ==========")
                .contains("FinishDecision answer=done")
                .doesNotContain("Authorization");
    }

    @Test
    void thinkingPhaseTruncatesOversizedReasoningContent() throws Exception {
        String longReasoning = "A".repeat(1200);
        startServer(200, completionWithMessage("{\"content\":\"\",\"reasoning_content\":\"" + longReasoning + "\"}"),
                new AtomicReference<String>(), new AtomicReference<JsonNode>());
        LmStudioModelProvider provider = new LmStudioModelProvider(
                new LmStudioConfig(baseUrl(), "qwen-local"));

        Decision decision = provider.decide(AgentContext.create(new Task("task-thinking-long", "think")),
                DecisionPhase.THINKING, Collections.<ToolDefinition>emptyList(), SYSTEM_PROMPT);

        assertThat(decision).isInstanceOf(ThinkingDecision.class);
        ThinkingDecision thinkingDecision = (ThinkingDecision) decision;
        assertThat(thinkingDecision.thought()).hasSizeLessThanOrEqualTo(840);
        assertThat(thinkingDecision.thought()).endsWith("...(truncated)");
    }

    @Test
    void throwsWhenHttpStatusIsNotSuccessful() throws Exception {
        startServer(500, "{\"error\":{\"message\":\"server error\"}}",
                new AtomicReference<String>(), new AtomicReference<JsonNode>());
        LmStudioModelProvider provider = new LmStudioModelProvider(
                new LmStudioConfig(baseUrl(), "qwen-local"));

        assertThatThrownBy(() -> provider.decide(AgentContext.create(new Task("task-4", "fail")),
                DecisionPhase.ACTION, Collections.<ToolDefinition>emptyList(), SYSTEM_PROMPT))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("LM Studio request failed: 500");
    }

    @Test
    void throwsWhenChoicesAreEmpty() throws Exception {
        startServer(200, "{\"choices\":[]}", new AtomicReference<String>(), new AtomicReference<JsonNode>());
        LmStudioModelProvider provider = new LmStudioModelProvider(
                new LmStudioConfig(baseUrl(), "qwen-local"));

        assertThatThrownBy(() -> provider.decide(AgentContext.create(new Task("task-5", "empty")),
                DecisionPhase.ACTION, Collections.<ToolDefinition>emptyList(), SYSTEM_PROMPT))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("LM Studio returned empty choices");
    }

    @Test
    void throwsWhenToolArgumentsAreInvalidJson() throws Exception {
        startServer(200, completionWithMessage("{\"tool_calls\":[{\"id\":\"call-1\",\"type\":\"function\","
                        + "\"function\":{\"name\":\"echo\",\"arguments\":\"not-json\"}}]}"),
                new AtomicReference<String>(), new AtomicReference<JsonNode>());
        LmStudioModelProvider provider = new LmStudioModelProvider(
                new LmStudioConfig(baseUrl(), "qwen-local"));

        assertThatThrownBy(() -> provider.decide(AgentContext.create(new Task("task-6", "bad json")),
                DecisionPhase.ACTION, Collections.<ToolDefinition>singletonList(new ToolDefinition(
                        "echo", "echo", Collections.<String, Object>singletonMap("type", "object"))),
                SYSTEM_PROMPT))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Invalid tool arguments JSON: not-json");
    }

    private void startServer(int statusCode, String responseBody,
            AtomicReference<String> authorization,
            AtomicReference<JsonNode> requestBody) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> handle(exchange, statusCode,
                responseBody, authorization, requestBody));
        server.start();
    }

    private void handle(HttpExchange exchange, int statusCode, String responseBody,
            AtomicReference<String> authorization,
            AtomicReference<JsonNode> requestBody) throws IOException {
        authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
        byte[] bytes = exchange.getRequestBody().readAllBytes();
        requestBody.set(MAPPER.readTree(bytes));
        byte[] response = responseBody.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
    }

    private static String completionWithMessage(String messageJson) {
        return completionWithMessage(messageJson, "stop");
    }

    private static String completionWithMessage(String messageJson, String finishReason) {
        return "{\"choices\":[{\"message\":" + messageJson + ",\"finish_reason\":\"" + finishReason + "\"}]}";
    }

    private void assertSystemPromptContains(JsonNode requestBody, String... expectedTexts) {
        String systemPrompt = requestBody.get("messages").get(0).get("content").asText();
        assertThat(systemPrompt).contains(expectedTexts);
    }

    private static String completionWithToolArguments(String arguments) throws Exception {
        ObjectNode message = MAPPER.createObjectNode();
        ArrayNode toolCalls = message.putArray("tool_calls");
        ObjectNode toolCall = toolCalls.addObject();
        toolCall.put("id", "call-1");
        toolCall.put("type", "function");
        ObjectNode function = toolCall.putObject("function");
        function.put("name", "echo");
        function.put("arguments", arguments);
        return completionWithMessage(MAPPER.writeValueAsString(message));
    }
}
