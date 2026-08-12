package com.ikunkk02.wishingwillow.ai;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ikunkk02.wishingwillow.program.WishProgram;
import com.ikunkk02.wishingwillow.program.WishProgramJson;
import com.ikunkk02.wishingwillow.program.WishProgramNormalizer;
import com.ikunkk02.wishingwillow.program.WishProgramNormalizationException;
import com.ikunkk02.wishingwillow.program.WishProgramNormalizationResult;
import com.ikunkk02.wishingwillow.program.WishProgramValidationIssue;
import com.ikunkk02.wishingwillow.wish.WishLifecycleLog;

import java.util.Arrays;
import java.util.Set;
import java.util.UUID;

/** JSON contract for the single AI Understanding call. */
public final class WishUnderstandingJson {
    private static final Gson GSON = new Gson();
    private static final Set<String> FIELDS = Set.of("decision", "rejection_code", "player_message", "reason",
            "interpretation", "program");
    private static final Set<String> LEGACY_ACCEPT_FIELDS = Set.of("interpretation", "program");

    private WishUnderstandingJson() { }

    public static Understanding parse(String raw) {
        return parse(raw, null);
    }

    public static Understanding parse(String raw, UUID sessionId) {
        JsonElement parsed;
        try {
            parsed = com.ikunkk02.wishingwillow.program.LlmJsonRecovery
                    .parseObject(raw, FIELDS, "MALFORMED_RESPONSE:UNDERSTANDING_JSON").object();
        } catch (RuntimeException error) {
            if (error instanceof IllegalArgumentException illegal) throw illegal;
            throw new IllegalArgumentException("MALFORMED_RESPONSE:UNDERSTANDING_JSON", error);
        }
        if (!parsed.isJsonObject()) {
            throw new IllegalArgumentException("MALFORMED_RESPONSE:UNDERSTANDING_FIELDS");
        }
        JsonObject root = parsed.getAsJsonObject();
        if (root.keySet().equals(LEGACY_ACCEPT_FIELDS)) {
            root.addProperty("decision", WishDecision.ACCEPT.name());
            root.addProperty("rejection_code", WishRejectionCode.NONE.name());
            root.addProperty("player_message", "");
            root.addProperty("reason", "");
        }
        if (!root.keySet().equals(FIELDS)) {
            throw new IllegalArgumentException("MALFORMED_RESPONSE:UNDERSTANDING_FIELDS");
        }
        WishDecision decision = enumValue(root, "decision", WishDecision.class);
        WishRejectionCode rejectionCode = enumValue(root, "rejection_code", WishRejectionCode.class);
        String playerMessage = string(root, "player_message", WishRejection.MAX_PLAYER_MESSAGE_LENGTH);
        String reason = string(root, "reason", WishRejection.MAX_REASON_LENGTH);
        if (decision == WishDecision.REJECT) {
            if (rejectionCode == WishRejectionCode.NONE || !isNull(root.get("interpretation"))
                    || !isNull(root.get("program"))) {
                throw new IllegalArgumentException("MALFORMED_RESPONSE:REJECT_SHAPE");
            }
            return new Understanding(decision, null, null,
                    WishRejection.sanitized(rejectionCode, playerMessage, reason));
        }
        if (rejectionCode != WishRejectionCode.NONE || !playerMessage.isEmpty() || !reason.isEmpty()
                || isNull(root.get("interpretation")) || isNull(root.get("program"))) {
            throw new IllegalArgumentException("MALFORMED_RESPONSE:ACCEPT_SHAPE");
        }
        WishInterpretation interpretation = WishInterpretationValidator.parseAndValidate(GSON.toJson(root.get("interpretation")));
        if (sessionId != null) WishLifecycleLog.event(sessionId, "NORMALIZATION_STARTED", "source=AI_RESPONSE");
        WishProgramNormalizationResult normalized = WishProgramNormalizer.normalize(root.get("program"));
        if (sessionId != null) WishLifecycleLog.event(sessionId, "NORMALIZATION_COMPLETED",
                "status=" + normalized.status() + " repairs=" + normalized.changes().size()
                        + " droppedActions=" + normalized.droppedActions());
        if (normalized.status() == com.ikunkk02.wishingwillow.program.WishProgramValidationStatus.REJECT) {
            WishProgramValidationIssue issue = normalized.issue();
            throw new WishProgramNormalizationException(issue == null
                    ? new WishProgramValidationIssue("INVALID_WISH_PROGRAM:UNKNOWN", "", "",
                    null, null, null, false, "normalizer rejected the program") : issue);
        }
        return new Understanding(decision, interpretation, normalized.requireProgram(), null);
    }

    public static JsonObject jsonSchema() {
        JsonObject root = new JsonObject();
        root.addProperty("type", "object"); root.addProperty("additionalProperties", false);
        com.google.gson.JsonArray required = new com.google.gson.JsonArray();
        FIELDS.stream().sorted().forEach(required::add); root.add("required", required);
        JsonObject properties = new JsonObject();
        properties.add("decision", enumSchema(WishDecision.values()));
        properties.add("rejection_code", enumSchema(WishRejectionCode.values()));
        properties.add("player_message", stringSchema(WishRejection.MAX_PLAYER_MESSAGE_LENGTH));
        properties.add("reason", stringSchema(WishRejection.MAX_REASON_LENGTH));
        properties.add("interpretation", WishInterpretationValidator.jsonSchema());
        properties.add("program", WishProgramJson.jsonSchema());
        properties.getAsJsonObject("interpretation").addProperty("nullable", true);
        properties.getAsJsonObject("program").addProperty("nullable", true);
        root.add("properties", properties);
        return root;
    }

    private static String stripFence(String raw) {
        if (raw == null) throw new IllegalArgumentException("MALFORMED_RESPONSE:NULL");
        String value = raw.strip();
        if (!value.startsWith("```")) return value;
        int newline = value.indexOf('\n');
        if (newline < 0 || !value.endsWith("```")) throw new IllegalArgumentException("MALFORMED_RESPONSE:FENCE");
        return value.substring(newline + 1, value.length() - 3).strip();
    }

    private static boolean isNull(JsonElement value) { return value == null || value.isJsonNull(); }

    private static String string(JsonObject object, String name, int maximum) {
        JsonElement value = object.get(name);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("MALFORMED_RESPONSE:STRING_" + name);
        }
        String result = value.getAsString().strip();
        if (result.length() > maximum) throw new IllegalArgumentException("MALFORMED_RESPONSE:LENGTH_" + name);
        return result;
    }

    private static <E extends Enum<E>> E enumValue(JsonObject object, String name, Class<E> type) {
        try { return Enum.valueOf(type, string(object, name, 64)); }
        catch (IllegalArgumentException error) { throw new IllegalArgumentException("MALFORMED_RESPONSE:ENUM_VALUE_" + type.getSimpleName()); }
    }

    private static JsonObject enumSchema(Enum<?>[] values) {
        JsonObject schema = stringSchema(64);
        com.google.gson.JsonArray enums = new com.google.gson.JsonArray();
        Arrays.stream(values).map(Enum::name).forEach(enums::add);
        schema.add("enum", enums);
        return schema;
    }

    private static JsonObject stringSchema(int maximum) {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "string");
        schema.addProperty("maxLength", maximum);
        return schema;
    }

    public record Understanding(WishDecision decision, WishInterpretation interpretation,
                                WishProgram program, WishRejection rejection) {
        public boolean accepted() { return decision == WishDecision.ACCEPT; }
    }
}
