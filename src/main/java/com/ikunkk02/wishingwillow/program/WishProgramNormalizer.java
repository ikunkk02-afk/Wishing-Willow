package com.ikunkk02.wishingwillow.program;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.ikunkk02.wishingwillow.WishingWillow;
import com.ikunkk02.wishingwillow.execution.action.WishActionDefinition;
import com.ikunkk02.wishingwillow.execution.action.WishActionRegistry;

import javax.annotation.Nullable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Tolerant, schema-driven boundary for untrusted AI Wish Programs.
 *
 * <p>It only performs deterministic repairs: bounded type coercion, numeric clamping, exact
 * action/enum canonicalization, declared defaults and removal of inert unknown fields. It never
 * relaxes the strict network/save/server validator and never guesses between ambiguous actions.</p>
 */
public final class WishProgramNormalizer {
    private static final Set<String> ROOT_FIELDS = Set.of(
            "schema_version", "goal", "core_actions", "presentation_actions", "skill", "unknown_capability");
    private static final Set<String> ACTION_FIELDS = Set.of("action", "parameters");
    private static final Set<String> FORBIDDEN_KEYS = Set.of(
            "command", "commands", "java", "script", "code", "nbt", "shell", "function", "execute");
    private static final Map<String, String> ACTION_ALIASES = Map.of(
            "change_mob_target", "set_entity_target",
            "falling_block_shower", "spawn_falling_block",
            "item_rain", "spawn_item_rain",
            "teleport", "teleport_player",
            "explosion", "create_explosion"
    );
    private static final WishActionRegistry ACTIONS = WishActionRegistry.defaults();

    private WishProgramNormalizer() { }

    public static WishProgramNormalizationResult normalize(String raw) {
        WishingWillow.LOGGER.info("Wish normalization started");
        LlmJsonRecovery.ParsedObject parsed;
        try {
            parsed = LlmJsonRecovery.parseObject(raw, Set.of("goal"),
                    "INVALID_WISH_PROGRAM:JSON_SYNTAX");
        } catch (IllegalArgumentException error) {
            return rejected(new WishProgramValidationIssue(error.getMessage(), "", "",
                    null, null, null, false, "AI response does not contain one recoverable JSON object"),
                    List.of(), 0);
        }
        State state = new State();
        if (parsed.recovered()) {
            state.change("", "json", null, parsed.object(), WishNormalizationReason.JSON_RECOVERY);
        }
        return normalize(parsed.object(), state);
    }

    public static WishProgramNormalizationResult normalize(JsonElement value) {
        WishingWillow.LOGGER.info("Wish normalization started");
        if (value == null || !value.isJsonObject()) {
            return rejected(new WishProgramValidationIssue("INVALID_WISH_PROGRAM:PROGRAM_OBJECT", "", "",
                    value, null, null, false, "program must be a JSON object"), List.of(), 0);
        }
        return normalize(value.getAsJsonObject(), new State());
    }

    private static WishProgramNormalizationResult normalize(JsonObject source, State state) {
        try {
            JsonObject root = source.deepCopy();
            rejectForbiddenKeys(root, "", true);
            for (String key : List.copyOf(root.keySet())) {
                if (!ROOT_FIELDS.contains(key)) {
                    JsonElement ignored = root.remove(key);
                    state.change("", key, ignored, null, WishNormalizationReason.UNKNOWN_FIELD_IGNORED);
                }
            }

            int schemaVersion = rootInteger(root, "schema_version", WishProgram.CURRENT_SCHEMA_VERSION, state);
            if (schemaVersion != WishProgram.CURRENT_SCHEMA_VERSION) {
                throw failure("INVALID_WISH_PROGRAM:SCHEMA_VERSION", "", "schema_version",
                        root.get("schema_version"), new JsonPrimitive(WishProgram.CURRENT_SCHEMA_VERSION),
                        new JsonPrimitive(WishProgram.CURRENT_SCHEMA_VERSION), false,
                        "unknown schema versions cannot be interpreted safely");
            }
            String goal = rootString(root, "goal", 512, false, null, state);
            String skill = rootString(root, "skill", 64, true, "", state);
            String unknown = rootString(root, "unknown_capability", 256, true, "", state);
            JsonArray coreSource = rootArray(root, "core_actions", state);
            JsonArray presentationSource = rootArray(root, "presentation_actions", state);

            List<WishProgramAction> core = normalizeActions(coreSource, false, state);
            List<WishProgramAction> presentation = normalizeActions(presentationSource, false, state);
            while (core.size() + presentation.size() > WishProgram.MAX_ACTIONS && !presentation.isEmpty()) {
                WishProgramAction dropped = presentation.remove(presentation.size() - 1);
                state.drop(dropped.action(), "presentation action exceeded program action budget",
                        WishNormalizationReason.ACTION_BUDGET_TRUNCATED);
            }
            while (core.size() + presentation.size() > WishProgram.MAX_ACTIONS && !core.isEmpty()) {
                WishProgramAction dropped = core.remove(core.size() - 1);
                state.drop(dropped.action(), "core action exceeded program action budget",
                        WishNormalizationReason.ACTION_BUDGET_TRUNCATED);
            }
            if (!unknown.isBlank() && (!core.isEmpty() || !skill.isBlank())) {
                throw failure("INVALID_WISH_PROGRAM:UNKNOWN_CAPABILITY_MUST_NOT_MIX_WITH_KNOWN_EXECUTION",
                        "", "unknown_capability", new JsonPrimitive(unknown), null, null, false,
                        "cannot infer whether known actions depend on the unknown capability");
            }
            if (core.isEmpty() && skill.isBlank() && unknown.isBlank()) {
                WishProgramValidationIssue issue = state.firstDroppedIssue == null
                        ? new WishProgramValidationIssue("INVALID_WISH_PROGRAM:NO_EXECUTABLE_ACTION", "", "",
                        null, null, null, false, "no recognizable action remains")
                        : state.firstDroppedIssue;
                return rejected(issue, state.changes, state.droppedActions);
            }

            WishProgram program = new WishProgram(schemaVersion, goal, core, presentation, skill, unknown);
            try {
                WishProgramJson.validate(program, ACTIONS);
            } catch (IllegalArgumentException error) {
                return rejected(new WishProgramValidationIssue(safeError(error), "", "", null,
                        null, null, false, "strict final validation rejected the normalized program"),
                        state.changes, state.droppedActions);
            }
            WishProgramValidationStatus status = state.changes.isEmpty() && state.droppedActions == 0
                    ? WishProgramValidationStatus.ACCEPT : WishProgramValidationStatus.REPAIRABLE;
            WishingWillow.LOGGER.info(
                    "Wish normalization completed repairs={} droppedActions={} status={}",
                    state.changes.size(), state.droppedActions,
                    status == WishProgramValidationStatus.ACCEPT ? "ACCEPT" : "REPAIRABLE_ACCEPTED");
            return new WishProgramNormalizationResult(status, program, state.changes,
                    state.droppedActions, null);
        } catch (ActionFailure error) {
            return rejected(error.issue, state.changes, state.droppedActions);
        } catch (RuntimeException error) {
            return rejected(new WishProgramValidationIssue(safeError(error), "", "", null,
                    null, null, false, "normalization could not safely recover the program"),
                    state.changes, state.droppedActions);
        }
    }

    private static List<WishProgramAction> normalizeActions(JsonArray values, boolean dependent, State state) {
        List<WishProgramAction> result = new ArrayList<>();
        for (JsonElement value : values) {
            try {
                result.add(normalizeAction(value, dependent, state));
            } catch (ActionFailure error) {
                if (dependent || error.fatal || error.issue.safetyCritical()) throw error;
                state.firstDroppedIssue = state.firstDroppedIssue == null ? error.issue : state.firstDroppedIssue;
                String action = error.issue.action().isBlank() ? "unknown" : error.issue.action();
                state.drop(action, error.issue.validationError(), WishNormalizationReason.ACTION_DROPPED);
            }
        }
        return result;
    }

    private static WishProgramAction normalizeAction(JsonElement value, boolean dependent, State state) {
        if (value == null || !value.isJsonObject()) {
            throw failure("INVALID_WISH_PROGRAM:ACTION_OBJECT", "", "", value,
                    null, null, false, dependent ? "dependent child is not an action object" : "action is not an object");
        }
        JsonObject object = value.getAsJsonObject().deepCopy();
        rejectForbiddenKeys(object, "", true);
        JsonElement actionValue = object.get("action");
        if (actionValue == null || !actionValue.isJsonPrimitive()
                || !actionValue.getAsJsonPrimitive().isString()) {
            throw failure("INVALID_WISH_PROGRAM:ACTION_NAME", "", "action", actionValue,
                    null, null, false, "action name is missing or not a string");
        }
        String originalAction = actionValue.getAsString();
        String action = actionId(originalAction);
        WishActionDefinition definition = ACTIONS.find(action);
        if (definition == null) {
            ActionFailure unknown = failure("INVALID_WISH_PROGRAM:UNKNOWN_ACTION_" + safeToken(originalAction),
                    originalAction, "action", actionValue, null, null, false,
                    dependent ? "unknown action is inside an ordered/flow dependency" : "unknown independent action");
            if (dependent) unknown.fatal = true;
            throw unknown;
        }
        if (!originalAction.equals(action)) {
            state.change(action, "action", new JsonPrimitive(originalAction), new JsonPrimitive(action),
                    WishNormalizationReason.ACTION_NAME_NORMALIZATION);
        }

        JsonObject parameters = new JsonObject();
        JsonElement parameterValue = object.get("parameters");
        JsonObject properties = definition.parameterSchema().getAsJsonObject("properties");
        if (parameterValue != null) {
            if (!parameterValue.isJsonObject()) {
                throw failure("INVALID_WISH_PROGRAM:ACTION_PARAMETERS", action, "parameters",
                        parameterValue, null, null, false, "parameters must be an object");
            }
            parameters = parameterValue.getAsJsonObject().deepCopy();
            for (String key : List.copyOf(object.keySet())) {
                if (!ACTION_FIELDS.contains(key) && properties.has(key) && !parameters.has(key)) {
                    JsonElement merged = object.get(key).deepCopy();
                    parameters.add(key, merged);
                    state.change(action, key, object.get(key), merged,
                            WishNormalizationReason.ROOT_FIELD_MERGED);
                }
            }
        } else {
            for (String key : List.copyOf(object.keySet())) {
                if (properties.has(key)) parameters.add(key, object.get(key).deepCopy());
            }
            state.change(action, "parameters", null, parameters,
                    WishNormalizationReason.ROOT_DEFAULT);
        }
        for (String key : List.copyOf(object.keySet())) {
            if (!ACTION_FIELDS.contains(key) && !properties.has(key)) {
                if (FORBIDDEN_KEYS.contains(key.toLowerCase(Locale.ROOT))) {
                    throw failure("INVALID_WISH_PROGRAM:FORBIDDEN_PARAMETER", action, key,
                            object.get(key), null, null, true, "forbidden executable field");
                }
                state.change(action, key, object.get(key), null,
                        WishNormalizationReason.UNKNOWN_FIELD_IGNORED);
            }
        }
        normalizeParameters(action, definition, parameters, state);
        if (definition.flowControl() && !definition.id().equals("delay")) {
            JsonArray children = parameters.getAsJsonArray("actions");
            List<WishProgramAction> normalizedChildren = normalizeActions(children, true, state);
            if (normalizedChildren.isEmpty()) {
                throw failure("INVALID_WISH_PROGRAM:FLOW_ACTIONS", action, "actions", children,
                        null, null, false, "flow action has no recoverable dependent children");
            }
            JsonArray normalizedArray = new JsonArray();
            for (WishProgramAction child : normalizedChildren) {
                JsonObject childJson = new JsonObject();
                childJson.addProperty("action", child.action());
                childJson.add("parameters", child.parameters());
                normalizedArray.add(childJson);
            }
            parameters.add("actions", normalizedArray);
        }
        return new WishProgramAction(action, parameters);
    }

    private static void normalizeParameters(String action, WishActionDefinition definition,
                                            JsonObject parameters, State state) {
        rejectForbiddenKeys(parameters, action, false);
        JsonObject schema = definition.parameterSchema();
        JsonObject properties = schema.getAsJsonObject("properties");
        for (String key : List.copyOf(parameters.keySet())) {
            JsonObject property = properties.has(key) && properties.get(key).isJsonObject()
                    ? properties.getAsJsonObject(key) : null;
            if (property == null) {
                JsonElement ignored = parameters.remove(key);
                state.change(action, key, ignored, null, WishNormalizationReason.UNKNOWN_FIELD_IGNORED);
                continue;
            }
            parameters.add(key, normalizeProperty(action, key, parameters.get(key), property, state));
        }
        for (String key : properties.keySet()) {
            if (parameters.has(key)) continue;
            JsonObject property = properties.getAsJsonObject(key);
            if (property.has("default")) {
                JsonElement defaultValue = property.get("default").deepCopy();
                parameters.add(key, defaultValue);
                state.change(action, key, null, defaultValue, WishNormalizationReason.DEFAULT_APPLIED);
            }
        }
        JsonArray required = schema.getAsJsonArray("required");
        if (required != null) {
            for (JsonElement requiredValue : required) {
                String key = requiredValue.getAsString();
                if (!parameters.has(key)) {
                    throw failure("INVALID_WISH_PROGRAM:MISSING_REQUIRED_PARAMETER_" + key,
                            action, key, null, null, null, false,
                            "required parameter has no declared default");
                }
            }
        }
    }

    private static JsonElement normalizeProperty(String action, String key, JsonElement raw,
                                                 JsonObject property, State state) {
        if (raw == null || raw.isJsonNull()) {
            throw failure("INVALID_WISH_PROGRAM:PARAMETER_TYPE_" + key, action, key, raw,
                    property.get("minimum"), property.get("maximum"), false, "null cannot be coerced safely");
        }
        String type = property.has("type") ? property.get("type").getAsString() : "";
        JsonElement value = raw.deepCopy();
        switch (type) {
            case "integer" -> value = normalizeNumber(action, key, value, property, true, state);
            case "number" -> value = normalizeNumber(action, key, value, property, false, state);
            case "boolean" -> {
                if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean()) break;
                if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
                    String text = value.getAsString().strip().toLowerCase(Locale.ROOT);
                    if (text.equals("true") || text.equals("false")) {
                        JsonElement normalized = new JsonPrimitive(Boolean.parseBoolean(text));
                        state.change(action, key, value, normalized, WishNormalizationReason.TYPE_COERCION);
                        value = normalized;
                        break;
                    }
                }
                throw failure("INVALID_WISH_PROGRAM:PARAMETER_TYPE_" + key, action, key, raw,
                        null, null, false, "boolean value cannot be coerced safely");
            }
            case "string" -> {
                if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
                    throw failure("INVALID_WISH_PROGRAM:PARAMETER_TYPE_" + key, action, key, raw,
                            null, null, false, "non-string value cannot be coerced safely");
                }
                String text = value.getAsString().strip();
                if (!text.equals(value.getAsString())) {
                    JsonElement normalized = new JsonPrimitive(text);
                    state.change(action, key, value, normalized, WishNormalizationReason.STRING_NORMALIZATION);
                    value = normalized;
                }
            }
            case "array" -> {
                if (!value.isJsonArray()) {
                    throw failure("INVALID_WISH_PROGRAM:PARAMETER_TYPE_" + key, action, key, raw,
                            null, null, false, "value must be an array");
                }
            }
            case "object" -> {
                if (!value.isJsonObject()) {
                    throw failure("INVALID_WISH_PROGRAM:PARAMETER_TYPE_" + key, action, key, raw,
                            null, null, false, "value must be an object");
                }
            }
            default -> throw failure("INVALID_WISH_PROGRAM:PARAMETER_SCHEMA_" + key, action, key,
                    raw, null, null, true, "registry property has no supported type");
        }
        if (property.has("enum")) value = normalizeEnum(action, key, value, property, state);
        return value;
    }

    private static JsonElement normalizeNumber(String action, String key, JsonElement raw,
                                               JsonObject property, boolean integer, State state) {
        BigDecimal number;
        boolean coerced = false;
        if (!raw.isJsonPrimitive()) {
            throw failure("INVALID_WISH_PROGRAM:PARAMETER_TYPE_" + key, action, key, raw,
                    property.get("minimum"), property.get("maximum"), false, "value is not numeric");
        }
        var primitive = raw.getAsJsonPrimitive();
        if (!primitive.isNumber() && !primitive.isString()) {
            throw failure("INVALID_WISH_PROGRAM:PARAMETER_TYPE_" + key, action, key, raw,
                    property.get("minimum"), property.get("maximum"), false, "value is not numeric");
        }
        try {
            number = new BigDecimal(primitive.getAsString().strip());
            coerced = primitive.isString();
        } catch (NumberFormatException error) {
            throw failure("INVALID_WISH_PROGRAM:PARAMETER_TYPE_" + key, action, key, raw,
                    property.get("minimum"), property.get("maximum"), false, "numeric string is invalid");
        }
        if (integer && number.stripTrailingZeros().scale() > 0) {
            throw failure("INVALID_WISH_PROGRAM:PARAMETER_TYPE_" + key, action, key, raw,
                    property.get("minimum"), property.get("maximum"), false,
                    "fractional value cannot be converted to an integer without losing information");
        }
        JsonElement value = integer
                ? new JsonPrimitive(number.toBigIntegerExact()) : new JsonPrimitive(number.stripTrailingZeros());
        if (coerced) state.change(action, key, raw, value, WishNormalizationReason.TYPE_COERCION);
        if (property.has("minimum")) {
            BigDecimal minimum = new BigDecimal(property.get("minimum").getAsString());
            if (number.compareTo(minimum) < 0) {
                JsonElement normalized = integer
                        ? new JsonPrimitive(minimum.toBigIntegerExact()) : new JsonPrimitive(minimum.stripTrailingZeros());
                state.change(action, key, value, normalized, WishNormalizationReason.MIN_CLAMP);
                value = normalized;
                number = minimum;
            }
        }
        if (property.has("maximum")) {
            BigDecimal maximum = new BigDecimal(property.get("maximum").getAsString());
            if (number.compareTo(maximum) > 0) {
                JsonElement normalized = integer
                        ? new JsonPrimitive(maximum.toBigIntegerExact()) : new JsonPrimitive(maximum.stripTrailingZeros());
                state.change(action, key, value, normalized, WishNormalizationReason.MAX_CLAMP);
                value = normalized;
            }
        }
        return value;
    }

    private static JsonElement normalizeEnum(String action, String key, JsonElement value,
                                             JsonObject property, State state) {
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw failure("INVALID_WISH_PROGRAM:PARAMETER_ENUM_" + key, action, key, value,
                    null, null, false, "enum value must be a string");
        }
        String provided = value.getAsString();
        String canonicalProvided = canonicalToken(provided);
        List<String> matches = new ArrayList<>();
        for (JsonElement allowed : property.getAsJsonArray("enum")) {
            if (canonicalToken(allowed.getAsString()).equals(canonicalProvided)) {
                matches.add(allowed.getAsString());
            }
        }
        if (matches.size() != 1) {
            throw failure("INVALID_WISH_PROGRAM:PARAMETER_ENUM_" + key, action, key, value,
                    null, null, false, matches.isEmpty() ? "enum meaning is unknown" : "enum match is ambiguous");
        }
        String normalized = matches.get(0);
        if (!provided.equals(normalized)) {
            JsonElement result = new JsonPrimitive(normalized);
            state.change(action, key, value, result, WishNormalizationReason.ENUM_NORMALIZATION);
            return result;
        }
        return value;
    }

    private static int rootInteger(JsonObject root, String key, int defaultValue, State state) {
        if (!root.has(key)) {
            JsonElement value = new JsonPrimitive(defaultValue);
            root.add(key, value);
            state.change("", key, null, value, WishNormalizationReason.ROOT_DEFAULT);
            return defaultValue;
        }
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "integer");
        JsonElement normalized = normalizeProperty("", key, root.get(key), schema, state);
        root.add(key, normalized);
        try {
            return normalized.getAsInt();
        } catch (RuntimeException error) {
            throw failure("INVALID_WISH_PROGRAM:INTEGER_" + key, "", key, normalized,
                    null, null, false, "integer exceeds supported range");
        }
    }

    private static String rootString(JsonObject root, String key, int max, boolean empty,
                                     @Nullable String defaultValue, State state) {
        if (!root.has(key)) {
            if (defaultValue == null) {
                throw failure("INVALID_WISH_PROGRAM:STRING_" + key, "", key, null,
                        null, null, false, "required root string is missing");
            }
            JsonElement value = new JsonPrimitive(defaultValue);
            root.add(key, value);
            state.change("", key, null, value, WishNormalizationReason.ROOT_DEFAULT);
            return defaultValue;
        }
        JsonElement raw = root.get(key);
        if (!raw.isJsonPrimitive() || !raw.getAsJsonPrimitive().isString()) {
            throw failure("INVALID_WISH_PROGRAM:STRING_" + key, "", key, raw,
                    null, null, false, "root field must be a string");
        }
        String value = raw.getAsString().strip();
        if (!value.equals(raw.getAsString())) {
            state.change("", key, raw, new JsonPrimitive(value), WishNormalizationReason.STRING_NORMALIZATION);
        }
        if (value.length() > max) {
            String truncated = value.substring(0, max);
            state.change("", key, new JsonPrimitive(value), new JsonPrimitive(truncated),
                    WishNormalizationReason.MAX_CLAMP);
            value = truncated;
        }
        if (!empty && value.isBlank()) {
            throw failure("INVALID_WISH_PROGRAM:STRING_" + key, "", key, raw,
                    null, null, false, "required root string is blank");
        }
        return value;
    }

    private static JsonArray rootArray(JsonObject root, String key, State state) {
        if (!root.has(key)) {
            JsonArray value = new JsonArray();
            root.add(key, value);
            state.change("", key, null, value, WishNormalizationReason.ROOT_DEFAULT);
            return value;
        }
        JsonElement value = root.get(key);
        if (!value.isJsonArray()) {
            throw failure("INVALID_WISH_PROGRAM:ACTION_ARRAY_" + key, "", key, value,
                    null, null, false, "action collection must be an array");
        }
        return value.getAsJsonArray();
    }

    private static String actionId(String value) {
        String canonical = canonicalToken(value);
        if (ACTIONS.find(canonical) != null) return canonical;
        String alias = ACTION_ALIASES.get(canonical);
        return alias == null ? canonical : alias;
    }

    private static String canonicalToken(String value) {
        return value == null ? "" : value.strip().toLowerCase(Locale.ROOT)
                .replaceAll("[\\s_-]+", "_")
                .replaceAll("^_+|_+$", "");
    }

    private static void rejectForbiddenKeys(JsonObject object, String action, boolean includeNested) {
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            if (FORBIDDEN_KEYS.contains(entry.getKey().toLowerCase(Locale.ROOT))) {
                throw failure("INVALID_WISH_PROGRAM:FORBIDDEN_PARAMETER", action, entry.getKey(),
                        entry.getValue(), null, null, true, "forbidden executable field");
            }
            if (!includeNested) continue;
            JsonElement value = entry.getValue();
            if (value != null && value.isJsonObject()) rejectForbiddenKeys(value.getAsJsonObject(), action, true);
            else if (value != null && value.isJsonArray()) {
                for (JsonElement child : value.getAsJsonArray()) {
                    if (child.isJsonObject()) rejectForbiddenKeys(child.getAsJsonObject(), action, true);
                }
            }
        }
    }

    private static WishProgramNormalizationResult rejected(WishProgramValidationIssue issue,
                                                           List<WishNormalizationChange> changes,
                                                           int droppedActions) {
        WishingWillow.LOGGER.warn(
                "Wish normalization completed repairs={} droppedActions={} status=REJECT error={}",
                changes.size(), droppedActions, issue.validationError());
        return new WishProgramNormalizationResult(WishProgramValidationStatus.REJECT, null,
                changes, droppedActions, issue);
    }

    private static ActionFailure failure(String code, String action, String parameter,
                                         @Nullable JsonElement provided,
                                         @Nullable JsonElement allowedMin,
                                         @Nullable JsonElement allowedMax,
                                         boolean safetyCritical, String detail) {
        return new ActionFailure(new WishProgramValidationIssue(code, action, parameter,
                provided, allowedMin, allowedMax, safetyCritical, detail));
    }

    private static String safeError(Throwable error) {
        String message = error == null ? null : error.getMessage();
        return message == null || message.isBlank() ? "INVALID_WISH_PROGRAM:UNKNOWN" : message;
    }

    private static String safeToken(String value) {
        String safe = canonicalToken(value).replaceAll("[^a-z0-9_]", "_");
        return safe.isBlank() ? "unknown" : safe.substring(0, Math.min(64, safe.length()));
    }

    private static String logValue(@Nullable JsonElement value) {
        if (value == null || value instanceof JsonNull) return "<missing>";
        String text = value.toString().replace('\n', ' ').replace('\r', ' ');
        return text.length() <= 128 ? text : text.substring(0, 128);
    }

    private static final class State {
        private final List<WishNormalizationChange> changes = new ArrayList<>();
        private int droppedActions;
        private WishProgramValidationIssue firstDroppedIssue;

        private void change(String action, String parameter, @Nullable JsonElement original,
                            @Nullable JsonElement normalized, WishNormalizationReason reason) {
            changes.add(new WishNormalizationChange(action, parameter, original, normalized, reason));
            switch (reason) {
                case ACTION_NAME_NORMALIZATION -> WishingWillow.LOGGER.info(
                        "Wish action normalized: original={} normalized={} reason={}",
                        logValue(original), logValue(normalized), reason);
                case UNKNOWN_FIELD_IGNORED -> WishingWillow.LOGGER.info(
                        "Ignored unknown optional field: action={} field={}", action, parameter);
                default -> WishingWillow.LOGGER.info(
                        "Wish parameter normalized: action={} parameter={} original={} normalized={} reason={}",
                        action, parameter, logValue(original), logValue(normalized), reason);
            }
        }

        private void drop(String action, String detail, WishNormalizationReason reason) {
            droppedActions++;
            changes.add(new WishNormalizationChange(action, "action", new JsonPrimitive(action),
                    null, reason));
            WishingWillow.LOGGER.info("Wish action dropped: action={} reason={} detail={}",
                    action, reason, detail);
        }
    }

    private static final class ActionFailure extends RuntimeException {
        private final WishProgramValidationIssue issue;
        private boolean fatal;

        private ActionFailure(WishProgramValidationIssue issue) {
            super(issue.validationError());
            this.issue = issue;
        }
    }
}
