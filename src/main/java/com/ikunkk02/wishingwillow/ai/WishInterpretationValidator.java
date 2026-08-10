package com.ikunkk02.wishingwillow.ai;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.internal.Streams;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class WishInterpretationValidator {
    public static final int MAX_INTENT_LENGTH = 64;
    public static final int MAX_LITERAL_GOAL_LENGTH = 512;
    public static final int MAX_TEXT_LENGTH = 1024;
    public static final int MAX_CAPABILITIES = 12;

    private static final Pattern INTENT_PATTERN = Pattern.compile("[a-z][a-z0-9_-]{0,63}");
    private static final Set<String> FIELDS = Set.of(
            "schema_version", "intent", "literal_goal", "loophole", "twisted_outcome",
            "reasoning_summary", "tone", "severity", "delivery", "required_capabilities"
    );
    private static final Gson GSON = new Gson();

    private WishInterpretationValidator() {
    }

    public static WishInterpretation parseAndValidate(String rawContent) {
        String json = stripSingleCodeFence(rawContent);
        JsonElement parsed = parseStrict(json);
        if (!parsed.isJsonObject()) {
            throw invalid();
        }
        JsonObject object = parsed.getAsJsonObject();
        if (!object.keySet().equals(FIELDS)) {
            throw invalid();
        }

        int schemaVersion = exactInteger(object, "schema_version");
        if (schemaVersion != 1) {
            throw invalid();
        }
        String intent = string(object, "intent", MAX_INTENT_LENGTH);
        if (!INTENT_PATTERN.matcher(intent).matches()) {
            throw invalid();
        }
        String literalGoal = string(object, "literal_goal", MAX_LITERAL_GOAL_LENGTH);
        String loophole = string(object, "loophole", MAX_TEXT_LENGTH);
        String twistedOutcome = string(object, "twisted_outcome", MAX_TEXT_LENGTH);
        String reasoningSummary = string(object, "reasoning_summary", MAX_TEXT_LENGTH);
        WishTone tone = enumValue(object, "tone", WishTone.class);
        int severity = exactInteger(object, "severity");
        if (severity < 0 || severity > 100) {
            throw invalid();
        }
        WishDelivery delivery = enumValue(object, "delivery", WishDelivery.class);

        JsonElement capabilitiesElement = object.get("required_capabilities");
        if (capabilitiesElement == null || !capabilitiesElement.isJsonArray()) {
            throw invalid();
        }
        JsonArray capabilitiesArray = capabilitiesElement.getAsJsonArray();
        if (capabilitiesArray.size() < 1 || capabilitiesArray.size() > MAX_CAPABILITIES) {
            throw invalid();
        }
        List<WishCapability> capabilities = new ArrayList<>();
        Set<WishCapability> seen = new HashSet<>();
        for (JsonElement element : capabilitiesArray) {
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
                throw invalid();
            }
            final WishCapability capability;
            try {
                capability = WishCapability.valueOf(element.getAsString());
            } catch (IllegalArgumentException exception) {
                throw invalid();
            }
            if (!seen.add(capability)) {
                throw invalid();
            }
            capabilities.add(capability);
        }
        return new WishInterpretation(
                schemaVersion, intent, literalGoal, loophole, twistedOutcome,
                reasoningSummary, tone, severity, delivery, capabilities
        );
    }

    /**
     * Accepts one narrowly repairable provider deviation while keeping the persisted and server-facing
     * interpretation contract strict. Some JSON-object-only providers emit an English intent label as
     * prose (for example, "obtain diamonds") even when the schema requires snake_case.
     */
    public static WishInterpretation parseProviderResponse(String rawContent) {
        try {
            return parseAndValidate(rawContent);
        } catch (IllegalArgumentException original) {
            final JsonElement parsed;
            try {
                parsed = parseStrict(stripSingleCodeFence(rawContent));
            } catch (IllegalArgumentException ignored) {
                throw original;
            }
            if (!parsed.isJsonObject() || !parsed.getAsJsonObject().keySet().equals(FIELDS)) {
                throw original;
            }
            JsonObject object = parsed.getAsJsonObject();
            JsonElement intentElement = object.get("intent");
            if (intentElement == null || !intentElement.isJsonPrimitive()
                    || !intentElement.getAsJsonPrimitive().isString()) {
                throw original;
            }
            String intent = intentElement.getAsString().strip();
            String normalized = normalizeIntent(intent);
            if (normalized.equals(intent) || !INTENT_PATTERN.matcher(normalized).matches()) {
                throw original;
            }
            object.addProperty("intent", normalized);
            return parseAndValidate(GSON.toJson(object));
        }
    }

    public static void validate(WishInterpretation interpretation) {
        parseAndValidate(toJson(interpretation));
    }

    public static String toJson(WishInterpretation interpretation) {
        JsonObject object = new JsonObject();
        object.addProperty("schema_version", interpretation.schemaVersion());
        object.addProperty("intent", interpretation.intent());
        object.addProperty("literal_goal", interpretation.literalGoal());
        object.addProperty("loophole", interpretation.loophole());
        object.addProperty("twisted_outcome", interpretation.twistedOutcome());
        object.addProperty("reasoning_summary", interpretation.reasoningSummary());
        object.addProperty("tone", interpretation.tone().name());
        object.addProperty("severity", interpretation.severity());
        object.addProperty("delivery", interpretation.delivery().name());
        JsonArray capabilities = new JsonArray();
        interpretation.requiredCapabilities().forEach(capability -> capabilities.add(capability.name()));
        object.add("required_capabilities", capabilities);
        return GSON.toJson(object);
    }

    public static JsonObject jsonSchema() {
        JsonObject schema = GSON.fromJson("""
                {
                  "type":"object",
                  "additionalProperties":false,
                  "required":["schema_version","intent","literal_goal","loophole","twisted_outcome","reasoning_summary","tone","severity","delivery","required_capabilities"],
                  "properties":{
                    "schema_version":{"type":"integer","const":1},
                    "intent":{"type":"string","minLength":1,"maxLength":64,"pattern":"^[a-z][a-z0-9_-]{0,63}$"},
                    "literal_goal":{"type":"string","minLength":1,"maxLength":512},
                    "loophole":{"type":"string","minLength":1,"maxLength":1024},
                    "twisted_outcome":{"type":"string","minLength":1,"maxLength":1024},
                    "reasoning_summary":{"type":"string","minLength":1,"maxLength":1024},
                    "tone":{"type":"string","enum":["NEUTRAL","IRONIC","DARK","HORROR","TRAGIC","ABSURD"]},
                    "severity":{"type":"integer","minimum":0,"maximum":100},
                    "delivery":{"type":"string","enum":["IMMEDIATE","DELAYED","CONDITIONAL","PROGRESSIVE","HIDDEN"]},
                    "required_capabilities":{"type":"array","minItems":1,"maxItems":12,"uniqueItems":true,"items":{"type":"string"}}
                  }
                }
                """, JsonObject.class);
        JsonArray capabilityValues = new JsonArray();
        for (WishCapability capability : WishCapability.values()) {
            capabilityValues.add(capability.name());
        }
        schema.getAsJsonObject("properties")
                .getAsJsonObject("required_capabilities")
                .getAsJsonObject("items")
                .add("enum", capabilityValues);
        return schema;
    }

    private static JsonElement parseStrict(String json) {
        try {
            JsonReader reader = new JsonReader(new StringReader(json));
            reader.setLenient(false);
            JsonElement result = Streams.parse(reader);
            if (reader.peek() != JsonToken.END_DOCUMENT) {
                throw invalid();
            }
            return result;
        } catch (Exception exception) {
            if (exception instanceof IllegalArgumentException illegalArgumentException) {
                throw illegalArgumentException;
            }
            throw invalid();
        }
    }

    private static String stripSingleCodeFence(String rawContent) {
        if (rawContent == null) {
            throw invalid();
        }
        String content = rawContent.strip();
        if (!content.startsWith("```")) {
            return content;
        }
        int firstNewline = content.indexOf('\n');
        if (firstNewline < 0 || !content.endsWith("```")) {
            throw invalid();
        }
        String language = content.substring(3, firstNewline).strip();
        if (!language.isEmpty() && !language.equalsIgnoreCase("json")) {
            throw invalid();
        }
        String body = content.substring(firstNewline + 1, content.length() - 3).strip();
        if (body.contains("```")) {
            throw invalid();
        }
        return body;
    }

    private static int exactInteger(JsonObject object, String name) {
        JsonElement element = object.get(name);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw invalid();
        }
        String value = element.getAsString();
        if (!value.matches("-?(0|[1-9][0-9]*)")) {
            throw invalid();
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw invalid();
        }
    }

    private static String string(JsonObject object, String name, int maxLength) {
        JsonElement element = object.get(name);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw invalid();
        }
        String value = element.getAsString().strip();
        if (value.isEmpty() || value.length() > maxLength) {
            throw invalid();
        }
        return value;
    }

    private static <E extends Enum<E>> E enumValue(JsonObject object, String name, Class<E> type) {
        String value = string(object, name, 64);
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException exception) {
            throw invalid();
        }
    }

    private static String normalizeIntent(String intent) {
        String normalized = intent.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_-]+", "_")
                .replaceAll("^[^a-z]+", "")
                .replaceAll("[_-]+$", "");
        if (normalized.length() > MAX_INTENT_LENGTH) {
            normalized = normalized.substring(0, MAX_INTENT_LENGTH).replaceAll("[_-]+$", "");
        }
        return normalized;
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException(AiErrorCategory.MALFORMED_RESPONSE.name());
    }
}
