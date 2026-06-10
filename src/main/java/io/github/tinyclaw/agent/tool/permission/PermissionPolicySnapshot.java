package io.github.tinyclaw.agent.tool.permission;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.github.tinyclaw.agent.domain.ToolCall;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 动态工具权限策略的不可变快照。
 */
public final class PermissionPolicySnapshot {

    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());
    private static final int SUPPORTED_VERSION = 1;

    private final boolean enabled;
    private final ToolPermissionAction defaultAction;
    private final Duration approvalTimeout;
    private final List<PermissionRule> rules;
    private final Path sourcePath;
    private final Instant loadedAt;

    private PermissionPolicySnapshot(boolean enabled, ToolPermissionAction defaultAction, Duration approvalTimeout,
            List<PermissionRule> rules, Path sourcePath, Instant loadedAt) {
        this.enabled = enabled;
        this.defaultAction = Objects.requireNonNull(defaultAction, "defaultAction");
        this.approvalTimeout = Objects.requireNonNull(approvalTimeout, "approvalTimeout");
        this.rules = Collections.unmodifiableList(new ArrayList<PermissionRule>(rules));
        this.sourcePath = sourcePath;
        this.loadedAt = Objects.requireNonNull(loadedAt, "loadedAt");
    }

    public static PermissionPolicySnapshot disabled(Path sourcePath) {
        return new PermissionPolicySnapshot(false, ToolPermissionAction.ALLOW,
                Duration.ofSeconds(ToolPermissionConfig.DEFAULT_APPROVAL_TIMEOUT_SECONDS),
                Collections.<PermissionRule>emptyList(), sourcePath, Instant.now());
    }

    public static PermissionPolicySnapshot fromLegacyConfig(ToolPermissionConfig config, Path sourcePath) {
        List<PermissionRule> rules = new ArrayList<PermissionRule>();
        for (Map.Entry<String, ToolPermissionAction> entry : config.toolActions().entrySet()) {
            rules.add(PermissionRule.toolAction("legacy-tool-" + entry.getKey(), entry.getKey(), entry.getValue()));
        }
        int index = 1;
        for (Pattern pattern : config.denyPatterns()) {
            rules.add(PermissionRule.argumentRegex("legacy-deny-" + index, "bash", "command",
                    pattern, ToolPermissionAction.DENY));
            index++;
        }
        return new PermissionPolicySnapshot(config.enabled(), ToolPermissionAction.ASK, config.approvalTimeout(),
                rules, sourcePath, Instant.now());
    }

    public static PermissionPolicySnapshot load(Path path) {
        if (path == null || !Files.exists(path)) {
            return disabled(path);
        }
        try {
            return parse(Files.readString(path), path, Instant.now());
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load permission policy: " + path, ex);
        }
    }

    static PermissionPolicySnapshot parse(String yaml, Path sourcePath, Instant loadedAt) {
        if (yaml == null || yaml.trim().isEmpty()) {
            throw new IllegalStateException("permissions yaml must not be empty");
        }
        JsonNode root;
        try {
            root = YAML_MAPPER.readTree(yaml);
        } catch (IOException ex) {
            throw new IllegalStateException("Invalid permissions yaml: " + ex.getMessage(), ex);
        }
        if (root == null || root.isNull() || root.isMissingNode()) {
            throw new IllegalStateException("permissions yaml must not be empty");
        }
        requireObject(root, "root");
        assertOnlyFields(root, Set.of("version", "enabled", "defaultAction", "approvalTimeoutSeconds", "rules"),
                "root");

        int version = intValue(root, "version", SUPPORTED_VERSION);
        if (version != SUPPORTED_VERSION) {
            throw new IllegalStateException("Unsupported permissions version: " + version);
        }
        boolean enabled = booleanValue(root, "enabled", false);
        ToolPermissionAction defaultAction = actionValue(root, "defaultAction", ToolPermissionAction.ASK);
        Duration timeout = Duration.ofSeconds(positiveInt(root, "approvalTimeoutSeconds",
                ToolPermissionConfig.DEFAULT_APPROVAL_TIMEOUT_SECONDS));
        List<PermissionRule> rules = parseRules(root.path("rules"));
        return new PermissionPolicySnapshot(enabled, defaultAction, timeout, rules, sourcePath, loadedAt);
    }

    public ToolPermissionDecision evaluate(ToolCall call) {
        if (!enabled) {
            return ToolPermissionDecision.allow("permissions disabled");
        }
        ToolPermissionDecision selected = null;
        for (PermissionRule rule : rules) {
            if (!rule.matches(call)) {
                continue;
            }
            ToolPermissionDecision candidate = rule.decision();
            if (selected == null || priority(candidate.action()) > priority(selected.action())) {
                selected = candidate;
            }
        }
        if (selected != null) {
            return selected;
        }
        return new ToolPermissionDecision(defaultAction, "Default permission action: " + defaultAction.name());
    }

    public boolean enabled() {
        return enabled;
    }

    public ToolPermissionAction defaultAction() {
        return defaultAction;
    }

    public Duration approvalTimeout() {
        return approvalTimeout;
    }

    public List<PermissionRule> rules() {
        return rules;
    }

    public Path sourcePath() {
        return sourcePath;
    }

    public Instant loadedAt() {
        return loadedAt;
    }

    public int ruleCount() {
        return rules.size();
    }

    private static List<PermissionRule> parseRules(JsonNode rulesNode) {
        if (rulesNode == null || rulesNode.isMissingNode() || rulesNode.isNull()) {
            return Collections.emptyList();
        }
        if (!rulesNode.isArray()) {
            throw new IllegalStateException("rules must be an array");
        }
        List<PermissionRule> rules = new ArrayList<PermissionRule>();
        Set<String> ids = new HashSet<String>();
        for (JsonNode ruleNode : rulesNode) {
            requireObject(ruleNode, "rule");
            assertOnlyFields(ruleNode, Set.of("id", "tools", "action", "arguments"), "rule");
            String id = textValue(ruleNode, "id", "");
            if (id.isBlank()) {
                throw new IllegalStateException("Permission rule id is required");
            }
            if (!ids.add(id)) {
                throw new IllegalStateException("Duplicate permission rule id: " + id);
            }
            List<String> tools = stringList(ruleNode.path("tools"), "tools");
            ToolPermissionAction action = actionValue(ruleNode, "action", null);
            if (action == null) {
                throw new IllegalStateException("Permission rule action is required: " + id);
            }
            Map<String, Pattern> argumentRegex = parseArguments(ruleNode.path("arguments"), id);
            rules.add(new PermissionRule(id, tools, action, argumentRegex));
        }
        return rules;
    }

    private static Map<String, Pattern> parseArguments(JsonNode argumentsNode, String ruleId) {
        if (argumentsNode == null || argumentsNode.isMissingNode() || argumentsNode.isNull()) {
            return Collections.emptyMap();
        }
        requireObject(argumentsNode, "arguments");
        Map<String, Pattern> patterns = new LinkedHashMap<String, Pattern>();
        Iterator<Map.Entry<String, JsonNode>> fields = argumentsNode.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            JsonNode condition = field.getValue();
            requireObject(condition, "arguments." + field.getKey());
            assertOnlyFields(condition, Set.of("regex"), "arguments." + field.getKey());
            String regex = normalizeRegex(textValue(condition, "regex", ""));
            if (regex.isBlank()) {
                throw new IllegalStateException("Regex is required for rule " + ruleId + " argument " + field.getKey());
            }
            try {
                patterns.put(field.getKey(), Pattern.compile(regex));
            } catch (PatternSyntaxException ex) {
                throw new IllegalStateException("Invalid regex for rule " + ruleId + ": " + regex, ex);
            }
        }
        return patterns;
    }

    private static String normalizeRegex(String regex) {
        return regex == null ? "" : regex.replace("\\\\", "\\");
    }

    private static void requireObject(JsonNode node, String name) {
        if (!node.isObject()) {
            throw new IllegalStateException(name + " must be an object");
        }
    }

    private static void assertOnlyFields(JsonNode node, Set<String> allowed, String context) {
        Iterator<String> fields = node.fieldNames();
        while (fields.hasNext()) {
            String field = fields.next();
            if (!allowed.contains(field)) {
                throw new IllegalStateException("Unknown field in " + context + ": " + field);
            }
        }
    }

    private static int intValue(JsonNode node, String field, int defaultValue) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return defaultValue;
        }
        if (!value.canConvertToInt()) {
            throw new IllegalStateException(field + " must be an integer");
        }
        return value.asInt();
    }

    private static int positiveInt(JsonNode node, String field, int defaultValue) {
        int value = intValue(node, field, defaultValue);
        if (value <= 0) {
            throw new IllegalStateException(field + " must be positive: " + value);
        }
        return value;
    }

    private static boolean booleanValue(JsonNode node, String field, boolean defaultValue) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return defaultValue;
        }
        if (!value.isBoolean()) {
            throw new IllegalStateException(field + " must be boolean");
        }
        return value.asBoolean();
    }

    private static String textValue(JsonNode node, String field, String defaultValue) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return defaultValue;
        }
        if (!value.isTextual()) {
            throw new IllegalStateException(field + " must be text");
        }
        return value.asText();
    }

    private static ToolPermissionAction actionValue(JsonNode node, String field, ToolPermissionAction defaultValue) {
        String value = textValue(node, field, null);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return ToolPermissionAction.parse(value, field);
    }

    private static List<String> stringList(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return Collections.emptyList();
        }
        if (!node.isArray()) {
            throw new IllegalStateException(field + " must be an array");
        }
        List<String> values = new ArrayList<String>();
        for (JsonNode item : node) {
            if (!item.isTextual()) {
                throw new IllegalStateException(field + " entries must be text");
            }
            values.add(item.asText());
        }
        return values;
    }

    private static int priority(ToolPermissionAction action) {
        if (action == ToolPermissionAction.DENY) {
            return 3;
        }
        if (action == ToolPermissionAction.ASK) {
            return 2;
        }
        return 1;
    }

    public static final class PermissionRule {
        private final String id;
        private final List<String> tools;
        private final ToolPermissionAction action;
        private final Map<String, Pattern> argumentRegex;

        private PermissionRule(String id, List<String> tools, ToolPermissionAction action,
                Map<String, Pattern> argumentRegex) {
            this.id = id;
            this.tools = Collections.unmodifiableList(new ArrayList<String>(tools));
            this.action = action;
            this.argumentRegex = Collections.unmodifiableMap(new LinkedHashMap<String, Pattern>(argumentRegex));
        }

        private static PermissionRule toolAction(String id, String tool, ToolPermissionAction action) {
            return new PermissionRule(id, Collections.singletonList(tool), action, Collections.<String, Pattern>emptyMap());
        }

        private static PermissionRule argumentRegex(String id, String tool, String argument, Pattern pattern,
                ToolPermissionAction action) {
            Map<String, Pattern> patterns = new LinkedHashMap<String, Pattern>();
            patterns.put(argument, pattern);
            return new PermissionRule(id, Collections.singletonList(tool), action, patterns);
        }

        private boolean matches(ToolCall call) {
            if (!tools.isEmpty() && !tools.contains(call.toolName())) {
                return false;
            }
            for (Map.Entry<String, Pattern> entry : argumentRegex.entrySet()) {
                Object value = call.arguments().get(entry.getKey());
                if (value == null || !entry.getValue().matcher(String.valueOf(value)).find()) {
                    return false;
                }
            }
            return true;
        }

        private ToolPermissionDecision decision() {
            return new ToolPermissionDecision(action,
                    "Permission rule " + id + " matched: " + action.name().toLowerCase(Locale.ROOT));
        }

        public String id() {
            return id;
        }

        public ToolPermissionAction action() {
            return action;
        }
    }
}
