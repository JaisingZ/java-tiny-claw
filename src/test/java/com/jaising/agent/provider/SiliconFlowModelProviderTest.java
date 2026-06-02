package com.jaising.agent.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jaising.agent.domain.AgentState;
import com.jaising.agent.domain.Decision;
import com.jaising.agent.domain.DecisionPhase;
import com.jaising.agent.domain.FinishDecision;
import com.jaising.agent.domain.Task;
import com.jaising.agent.domain.ThinkingDecision;
import com.jaising.agent.domain.ToolCall;
import com.jaising.agent.domain.ToolDecision;
import com.jaising.agent.domain.ToolDefinition;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class SiliconFlowModelProviderTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void parsesActionTextResponseAsFinishDecisionAndSendsAuthorizationHeader() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<String>();
        AtomicReference<JsonNode> requestBody = new AtomicReference<JsonNode>();
        startServer(200, completionWithMessage("{\"content\":\"done\"}"), authorization, requestBody);

        SiliconFlowModelProvider provider = new SiliconFlowModelProvider(
                new SiliconFlowConfig("test-key", baseUrl(), "Qwen/Qwen3-8B"));

        Decision decision = provider.decide(AgentState.create(new Task("task-1", "finish it")),
                DecisionPhase.ACTION, Collections.<ToolDefinition>emptyList());

        assertThat(decision).isEqualTo(new FinishDecision("done"));
        assertThat(authorization.get()).isEqualTo("Bearer test-key");
        assertThat(requestBody.get().get("stream").asBoolean()).isFalse();
        assertThat(requestBody.get().get("model").asText()).isEqualTo("Qwen/Qwen3-8B");
        assertThat(requestBody.get().has("tools")).isFalse();
    }

    @Test
    void thinkingPhaseDoesNotSendToolsAndUsesReasoningContentFallback() throws Exception {
        AtomicReference<JsonNode> requestBody = new AtomicReference<JsonNode>();
        startServer(200, completionWithMessage("{\"content\":\"\",\"reasoning_content\":\"think first\"}"),
                new AtomicReference<String>(), requestBody);
        SiliconFlowModelProvider provider = new SiliconFlowModelProvider(
                new SiliconFlowConfig("test-key", baseUrl(), "Qwen/Qwen3-8B"));
        ToolDefinition tool = new ToolDefinition("echo", "echo",
                Collections.<String, Object>singletonMap("type", "object"));

        Decision decision = provider.decide(AgentState.create(new Task("task-2", "think")),
                DecisionPhase.THINKING, Collections.singletonList(tool));

        assertThat(decision).isEqualTo(new ThinkingDecision("think first"));
        assertThat(requestBody.get().has("tools")).isFalse();
        assertSystemPromptContains(requestBody.get(),
                "powershell -NoProfile -NonInteractive -Command",
                "Do not use && or ||",
                "Do not use ; to mean run the next command only when the previous command succeeds",
                "$LASTEXITCODE",
                "write_file",
                "UTF-8",
                "Set-Content",
                "Out-File",
                "javac target/Hello.java; if ($LASTEXITCODE -eq 0) { java -cp target Hello } else { exit $LASTEXITCODE }");
    }

    @Test
    void actionPhaseSendsToolsAndParsesFirstToolCall() throws Exception {
        AtomicReference<JsonNode> requestBody = new AtomicReference<JsonNode>();
        startServer(200, completionWithMessage("{\"tool_calls\":[{\"id\":\"call-1\",\"type\":\"function\","
                        + "\"function\":{\"name\":\"echo\",\"arguments\":\"{\\\"text\\\":\\\"hello\\\"}\"}}]}"),
                new AtomicReference<String>(), requestBody);
        SiliconFlowModelProvider provider = new SiliconFlowModelProvider(
                new SiliconFlowConfig("test-key", baseUrl(), "Qwen/Qwen3-8B"));
        ToolDefinition tool = new ToolDefinition("echo", "回显文本",
                Collections.<String, Object>singletonMap("type", "object"));

        Decision decision = provider.decide(AgentState.create(new Task("task-3", "echo hello")),
                DecisionPhase.ACTION, Collections.singletonList(tool));

        assertThat(requestBody.get().get("tools")).hasSize(1);
        assertThat(requestBody.get().get("tools").get(0).get("type").asText()).isEqualTo("function");
        assertThat(requestBody.get().get("tools").get(0).get("function").get("name").asText()).isEqualTo("echo");
        assertSystemPromptContains(requestBody.get(),
                "powershell -NoProfile -NonInteractive -Command",
                "Do not use && or ||",
                "Do not use ; to mean run the next command only when the previous command succeeds",
                "$LASTEXITCODE",
                "write_file",
                "UTF-8",
                "Set-Content",
                "Out-File",
                "javac target/Hello.java; if ($LASTEXITCODE -eq 0) { java -cp target Hello } else { exit $LASTEXITCODE }",
                "必须输出最终回答，或在需要时调用一个或多个独立工具",
                "如果多个操作互相独立",
                "Observation 已经满足用户目标",
                "不要重复调用相同工具");
        assertThat(decision).isEqualTo(new ToolDecision(new ToolCall("echo",
                Collections.<String, Object>singletonMap("text", "hello"))));
    }

    @Test
    void parsesMarkdownFencedToolArguments() throws Exception {
        startServer(200, completionWithToolArguments("```json\n{\"text\":\"hello\"}\n```"),
                new AtomicReference<String>(), new AtomicReference<JsonNode>());
        SiliconFlowModelProvider provider = new SiliconFlowModelProvider(
                new SiliconFlowConfig("test-key", baseUrl(), "Qwen/Qwen3-8B"));

        Decision decision = provider.decide(AgentState.create(new Task("task-fenced", "echo hello")),
                DecisionPhase.ACTION, Collections.<ToolDefinition>singletonList(new ToolDefinition(
                        "echo", "echo", Collections.<String, Object>singletonMap("type", "object"))));

        assertThat(decision).isEqualTo(new ToolDecision(new ToolCall("echo",
                Collections.<String, Object>singletonMap("text", "hello"))));
    }

    @Test
    void parsesDoubleEscapedToolArguments() throws Exception {
        startServer(200, completionWithToolArguments("\"{\\\"text\\\":\\\"hello\\\"}\""),
                new AtomicReference<String>(), new AtomicReference<JsonNode>());
        SiliconFlowModelProvider provider = new SiliconFlowModelProvider(
                new SiliconFlowConfig("test-key", baseUrl(), "Qwen/Qwen3-8B"));

        Decision decision = provider.decide(AgentState.create(new Task("task-escaped", "echo hello")),
                DecisionPhase.ACTION, Collections.<ToolDefinition>singletonList(new ToolDefinition(
                        "echo", "echo", Collections.<String, Object>singletonMap("type", "object"))));

        assertThat(decision).isEqualTo(new ToolDecision(new ToolCall("echo",
                Collections.<String, Object>singletonMap("text", "hello"))));
    }

    @Test
    void parsesToolArgumentsWrappedInText() throws Exception {
        startServer(200, completionWithToolArguments("arguments are {\"text\":\"hello\"}"),
                new AtomicReference<String>(), new AtomicReference<JsonNode>());
        SiliconFlowModelProvider provider = new SiliconFlowModelProvider(
                new SiliconFlowConfig("test-key", baseUrl(), "Qwen/Qwen3-8B"));

        Decision decision = provider.decide(AgentState.create(new Task("task-wrapped", "echo hello")),
                DecisionPhase.ACTION, Collections.<ToolDefinition>singletonList(new ToolDefinition(
                        "echo", "echo", Collections.<String, Object>singletonMap("type", "object"))));

        assertThat(decision).isEqualTo(new ToolDecision(new ToolCall("echo",
                Collections.<String, Object>singletonMap("text", "hello"))));
    }

    @Test
    void parsesToolArgumentsMissingTrailingObjectBrace() throws Exception {
        startServer(200, completionWithToolArguments("{\"text\":\"hello\""),
                new AtomicReference<String>(), new AtomicReference<JsonNode>());
        SiliconFlowModelProvider provider = new SiliconFlowModelProvider(
                new SiliconFlowConfig("test-key", baseUrl(), "Qwen/Qwen3-8B"));

        Decision decision = provider.decide(AgentState.create(new Task("task-missing-brace", "echo hello")),
                DecisionPhase.ACTION, Collections.<ToolDefinition>singletonList(new ToolDefinition(
                        "echo", "echo", Collections.<String, Object>singletonMap("type", "object"))));

        assertThat(decision).isEqualTo(new ToolDecision(new ToolCall("echo",
                Collections.<String, Object>singletonMap("text", "hello"))));
    }

    @Test
    void parsesToolArgumentsMissingTrailingBraceWhenTextContainsBraces() throws Exception {
        startServer(200, completionWithToolArguments("{\"text\":\"literal { and } braces\""),
                new AtomicReference<String>(), new AtomicReference<JsonNode>());
        SiliconFlowModelProvider provider = new SiliconFlowModelProvider(
                new SiliconFlowConfig("test-key", baseUrl(), "Qwen/Qwen3-8B"));

        Decision decision = provider.decide(AgentState.create(new Task("task-string-braces", "echo braces")),
                DecisionPhase.ACTION, Collections.<ToolDefinition>singletonList(new ToolDefinition(
                        "echo", "echo", Collections.<String, Object>singletonMap("type", "object"))));

        assertThat(decision).isEqualTo(new ToolDecision(new ToolCall("echo",
                Collections.<String, Object>singletonMap("text", "literal { and } braces"))));
    }

    @Test
    void debugOutputPrintsRequestResponsePhaseAndDecision() throws Exception {
        AtomicReference<JsonNode> requestBody = new AtomicReference<JsonNode>();
        startServer(200, completionWithMessage("{\"content\":\"done\"}"),
                new AtomicReference<String>(), requestBody);
        StringBuilder debugOutput = new StringBuilder();
        SiliconFlowModelProvider provider = new SiliconFlowModelProvider(
                new SiliconFlowConfig("test-key", baseUrl(), "Qwen/Qwen3-8B"),
                line -> debugOutput.append(line).append('\n'));

        provider.decide(AgentState.create(new Task("task-debug", "debug it")),
                DecisionPhase.ACTION, Collections.<ToolDefinition>emptyList());

        assertThat(debugOutput.toString())
                .contains("========== [Provider][ACTION] Request JSON ==========")
                .contains("\"model\" : \"Qwen/Qwen3-8B\"")
                .contains("\"content\" : \"debug it\"")
                .contains("========== [Provider][ACTION] Response JSON ==========")
                .contains("\"content\" : \"done\"")
                .contains("========== [Provider][ACTION] Parsed Decision ==========")
                .contains("FinishDecision answer=done")
                .doesNotContain("Bearer test-key");
    }

    @Test
    void throwsWhenHttpStatusIsNotSuccessful() throws Exception {
        startServer(401, "{\"error\":{\"message\":\"unauthorized\"}}",
                new AtomicReference<String>(), new AtomicReference<JsonNode>());
        SiliconFlowModelProvider provider = new SiliconFlowModelProvider(
                new SiliconFlowConfig("bad-key", baseUrl(), "Qwen/Qwen3-8B"));

        assertThatThrownBy(() -> provider.decide(AgentState.create(new Task("task-4", "fail")),
                DecisionPhase.ACTION, Collections.<ToolDefinition>emptyList()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("SiliconFlow request failed: 401");
    }

    @Test
    void throwsWhenChoicesAreEmpty() throws Exception {
        startServer(200, "{\"choices\":[]}", new AtomicReference<String>(), new AtomicReference<JsonNode>());
        SiliconFlowModelProvider provider = new SiliconFlowModelProvider(
                new SiliconFlowConfig("test-key", baseUrl(), "Qwen/Qwen3-8B"));

        assertThatThrownBy(() -> provider.decide(AgentState.create(new Task("task-5", "empty")),
                DecisionPhase.ACTION, Collections.<ToolDefinition>emptyList()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("SiliconFlow returned empty choices");
    }

    @Test
    void throwsWhenToolArgumentsAreInvalidJson() throws Exception {
        startServer(200, completionWithMessage("{\"tool_calls\":[{\"id\":\"call-1\",\"type\":\"function\","
                        + "\"function\":{\"name\":\"echo\",\"arguments\":\"not-json\"}}]}"),
                new AtomicReference<String>(), new AtomicReference<JsonNode>());
        SiliconFlowModelProvider provider = new SiliconFlowModelProvider(
                new SiliconFlowConfig("test-key", baseUrl(), "Qwen/Qwen3-8B"));

        assertThatThrownBy(() -> provider.decide(AgentState.create(new Task("task-6", "bad json")),
                DecisionPhase.ACTION, Collections.<ToolDefinition>singletonList(new ToolDefinition(
                        "echo", "echo", Collections.<String, Object>singletonMap("type", "object")))))
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
        return "{\"choices\":[{\"message\":" + messageJson + ",\"finish_reason\":\"stop\"}]}";
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
