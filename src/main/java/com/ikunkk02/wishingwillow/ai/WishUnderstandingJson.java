package com.ikunkk02.wishingwillow.ai;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ikunkk02.wishingwillow.program.WishProgram;
import com.ikunkk02.wishingwillow.program.WishProgramJson;

import java.util.Set;

/** JSON contract for the single AI Understanding call. */
public final class WishUnderstandingJson {
    private static final Gson GSON = new Gson();
    private static final Set<String> FIELDS = Set.of("interpretation", "program");

    private WishUnderstandingJson() { }

    public static Understanding parse(String raw) {
        JsonElement parsed;
        try { parsed = JsonParser.parseString(stripFence(raw)); }
        catch (RuntimeException error) { throw new IllegalArgumentException("MALFORMED_RESPONSE:UNDERSTANDING_JSON", error); }
        if (!parsed.isJsonObject() || !parsed.getAsJsonObject().keySet().equals(FIELDS)) {
            throw new IllegalArgumentException("MALFORMED_RESPONSE:UNDERSTANDING_FIELDS");
        }
        JsonObject root = parsed.getAsJsonObject();
        WishInterpretation interpretation = WishInterpretationValidator.parseAndValidate(GSON.toJson(root.get("interpretation")));
        WishProgram program = WishProgramJson.parseAndValidate(GSON.toJson(root.get("program")));
        return new Understanding(interpretation, program);
    }

    public static JsonObject jsonSchema() {
        JsonObject root = new JsonObject();
        root.addProperty("type", "object"); root.addProperty("additionalProperties", false);
        com.google.gson.JsonArray required = new com.google.gson.JsonArray();
        required.add("interpretation"); required.add("program"); root.add("required", required);
        JsonObject properties = new JsonObject();
        properties.add("interpretation", WishInterpretationValidator.jsonSchema());
        properties.add("program", WishProgramJson.jsonSchema());
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

    public record Understanding(WishInterpretation interpretation, WishProgram program) { }
}
