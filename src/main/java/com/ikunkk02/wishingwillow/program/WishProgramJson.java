package com.ikunkk02.wishingwillow.program;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.internal.Streams;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.ikunkk02.wishingwillow.execution.action.WishActionDefinition;
import com.ikunkk02.wishingwillow.execution.action.WishActionRegistry;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Strict JSON/network/save boundary for Wish Programs. */
public final class WishProgramJson {
    public static final int MAX_JSON = 64 * 1024;
    private static final Set<String> ROOT_FIELDS = Set.of(
            "schema_version", "goal", "core_actions", "presentation_actions", "skill", "unknown_capability");
    private static final Set<String> ACTION_FIELDS = Set.of("action", "parameters");
    private static final Set<String> FORBIDDEN_KEYS = Set.of(
            "command", "commands", "java", "script", "code", "nbt", "shell", "function", "execute");
    private static final Gson GSON = new Gson();

    private WishProgramJson() { }

    public static WishProgram parseAndValidate(String raw) {
        if (raw == null || raw.length() > MAX_JSON) throw invalid("PROGRAM_SIZE");
        JsonElement parsed = parseStrict(stripFence(raw));
        if (!parsed.isJsonObject() || !parsed.getAsJsonObject().keySet().equals(ROOT_FIELDS)) {
            throw invalid("PROGRAM_FIELDS");
        }
        JsonObject root = parsed.getAsJsonObject();
        int schema = integer(root, "schema_version");
        String goal = string(root, "goal", 512, false);
        List<WishProgramAction> core = actions(root, "core_actions");
        List<WishProgramAction> presentation = actions(root, "presentation_actions");
        String skill = string(root, "skill", 64, true);
        String unknown = string(root, "unknown_capability", 256, true);
        WishProgram program = new WishProgram(schema, goal, core, presentation, skill, unknown);
        validate(program, WishActionRegistry.defaults());
        return program;
    }

    public static void validate(WishProgram program, WishActionRegistry registry) {
        for (WishProgramAction action : concat(program.coreActions(), program.presentationActions())) {
            WishActionDefinition definition = registry.find(action.action());
            if (definition == null) throw invalid("UNKNOWN_ACTION_" + action.action());
            validateParameters(action.parameters(), definition, registry, 0);
        }
        int total = countExpanded(program.coreActions(), 0) + countExpanded(program.presentationActions(), 0);
        if (total > WishProgram.MAX_ACTIONS) throw invalid("PROGRAM_ACTION_BUDGET");
        if (program.requiresAgent() && (!program.coreActions().isEmpty() || program.usesSkill())) {
            throw invalid("UNKNOWN_CAPABILITY_MUST_NOT_MIX_WITH_KNOWN_EXECUTION");
        }
    }

    public static String toJson(WishProgram program) {
        JsonObject root = new JsonObject();
        root.addProperty("schema_version", program.schemaVersion());
        root.addProperty("goal", program.goal());
        root.add("core_actions", actionArray(program.coreActions()));
        root.add("presentation_actions", actionArray(program.presentationActions()));
        root.addProperty("skill", program.skill());
        root.addProperty("unknown_capability", program.unknownCapability());
        return GSON.toJson(root);
    }

    public static JsonObject jsonSchema() {
        JsonObject root = JsonParser.parseString("""
                {"type":"object","additionalProperties":false,
                 "required":["schema_version","goal","core_actions","presentation_actions","skill","unknown_capability"],
                 "properties":{
                   "schema_version":{"type":"integer","const":1},
                   "goal":{"type":"string","minLength":1,"maxLength":512},
                   "core_actions":{"type":"array","minItems":0,"maxItems":32},
                   "presentation_actions":{"type":"array","minItems":0,"maxItems":8},
                   "skill":{"type":"string","maxLength":64},
                   "unknown_capability":{"type":"string","maxLength":256}
                 }}
                """).getAsJsonObject();
        JsonObject action = JsonParser.parseString("""
                {"type":"object","additionalProperties":false,"required":["action","parameters"],
                 "properties":{"action":{"type":"string"},"parameters":{"type":"object"}}}
                """).getAsJsonObject();
        JsonArray ids = new JsonArray();
        WishActionRegistry.defaults().ids().forEach(ids::add);
        action.getAsJsonObject("properties").getAsJsonObject("action").add("enum", ids);
        root.getAsJsonObject("properties").getAsJsonObject("core_actions").add("items", action.deepCopy());
        root.getAsJsonObject("properties").getAsJsonObject("presentation_actions").add("items", action.deepCopy());
        return root;
    }

    static String canonical(JsonElement element) {
        return GSON.toJson(canonicalElement(element));
    }

    private static void validateParameters(JsonObject parameters, WishActionDefinition definition,
                                           WishActionRegistry registry, int depth) {
        if (depth > 4) throw invalid("FLOW_DEPTH");
        for (String key : parameters.keySet()) {
            if (FORBIDDEN_KEYS.contains(key.toLowerCase(Locale.ROOT))) throw invalid("FORBIDDEN_PARAMETER");
        }
        rejectExecutableText(parameters);
        JsonObject properties = definition.parameterSchema().getAsJsonObject("properties");
        if (properties != null && !properties.keySet().containsAll(parameters.keySet())) {
            throw invalid("UNDECLARED_PARAMETER_" + definition.id());
        }
        if (definition.flowControl()) {
            if (definition.id().equals("repeat")) {
                int count = integer(parameters, "count");
                if (count < 1 || count > 16) throw invalid("REPEAT_COUNT");
            }
            if (definition.id().equals("delay")) {
                int ticks = integer(parameters, "ticks");
                if (ticks < 1 || ticks > 1200) throw invalid("DELAY_TICKS");
            }
            if (!definition.id().equals("delay")) {
                JsonElement children = parameters.get("actions");
                if (children == null || !children.isJsonArray() || children.getAsJsonArray().isEmpty()
                        || children.getAsJsonArray().size() > 16) throw invalid("FLOW_ACTIONS");
                for (JsonElement child : children.getAsJsonArray()) {
                    WishProgramAction parsed = parseAction(child);
                    WishActionDefinition childDefinition = registry.find(parsed.action());
                    if (childDefinition == null) throw invalid("UNKNOWN_ACTION_" + parsed.action());
                    validateParameters(parsed.parameters(), childDefinition, registry, depth + 1);
                }
            }
        }
    }

    private static int countExpanded(List<WishProgramAction> actions, int depth) {
        if (depth > 4) throw invalid("FLOW_DEPTH");
        int count = 0;
        for (WishProgramAction action : actions) {
            if (action.action().equals("delay")) { count++; continue; }
            if (Set.of("sequence", "parallel", "repeat").contains(action.action())) {
                JsonArray children = action.parameters().getAsJsonArray("actions");
                List<WishProgramAction> parsed = new ArrayList<>();
                children.forEach(child -> parsed.add(parseAction(child)));
                int childCount = countExpanded(parsed, depth + 1);
                count += action.action().equals("repeat") ? childCount * integer(action.parameters(), "count") : childCount;
            } else count++;
        }
        return count;
    }

    private static List<WishProgramAction> actions(JsonObject root, String name) {
        JsonElement value = root.get(name);
        if (value == null || !value.isJsonArray() || value.getAsJsonArray().size() > WishProgram.MAX_ACTIONS) {
            throw invalid("ACTION_ARRAY_" + name);
        }
        List<WishProgramAction> result = new ArrayList<>();
        value.getAsJsonArray().forEach(element -> result.add(parseAction(element)));
        return List.copyOf(result);
    }

    private static WishProgramAction parseAction(JsonElement value) {
        if (!value.isJsonObject() || !value.getAsJsonObject().keySet().equals(ACTION_FIELDS)) throw invalid("ACTION_FIELDS");
        JsonObject object = value.getAsJsonObject();
        String action = string(object, "action", 64, false);
        JsonElement parameters = object.get("parameters");
        if (parameters == null || !parameters.isJsonObject()) throw invalid("ACTION_PARAMETERS");
        return new WishProgramAction(action, parameters.getAsJsonObject());
    }

    private static JsonArray actionArray(List<WishProgramAction> actions) {
        JsonArray array = new JsonArray();
        actions.forEach(action -> {
            JsonObject value = new JsonObject();
            value.addProperty("action", action.action()); value.add("parameters", action.parameters());
            array.add(value);
        });
        return array;
    }

    private static void rejectExecutableText(JsonElement value) {
        if (value.isJsonArray()) value.getAsJsonArray().forEach(WishProgramJson::rejectExecutableText);
        else if (value.isJsonObject()) value.getAsJsonObject().entrySet().forEach(entry -> rejectExecutableText(entry.getValue()));
        else if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
            String text = value.getAsString().strip().toLowerCase(Locale.ROOT);
            if (text.startsWith("/") || text.contains("while(true)") || text.contains("runtime.getruntime")
                    || text.contains("system.exit") || text.contains("```")) throw invalid("EXECUTABLE_TEXT");
        }
    }

    private static List<WishProgramAction> concat(List<WishProgramAction> first, List<WishProgramAction> second) {
        List<WishProgramAction> result = new ArrayList<>(first); result.addAll(second); return result;
    }

    private static int integer(JsonObject object, String name) {
        JsonElement value = object.get(name);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()
                || !value.getAsString().matches("-?(0|[1-9][0-9]*)")) throw invalid("INTEGER_" + name);
        try { return value.getAsInt(); } catch (RuntimeException error) { throw invalid("INTEGER_" + name); }
    }

    private static String string(JsonObject object, String name, int max, boolean empty) {
        JsonElement value = object.get(name);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw invalid("STRING_" + name);
        String result = value.getAsString().strip();
        if ((!empty && result.isBlank()) || result.length() > max) throw invalid("STRING_" + name);
        return result;
    }

    private static JsonElement parseStrict(String json) {
        try {
            JsonReader reader = new JsonReader(new StringReader(json)); reader.setLenient(false);
            JsonElement value = Streams.parse(reader);
            if (reader.peek() != JsonToken.END_DOCUMENT) throw invalid("TRAILING_JSON");
            return value;
        } catch (RuntimeException error) {
            if (error instanceof IllegalArgumentException illegal) throw illegal;
            throw invalid("JSON_SYNTAX");
        } catch (Exception error) { throw invalid("JSON_SYNTAX"); }
    }

    private static String stripFence(String raw) {
        String value = raw.strip();
        if (!value.startsWith("```")) return value;
        int newline = value.indexOf('\n');
        if (newline < 0 || !value.endsWith("```")) throw invalid("CODE_FENCE");
        return value.substring(newline + 1, value.length() - 3).strip();
    }

    private static JsonElement canonicalElement(JsonElement element) {
        if (element.isJsonArray()) {
            JsonArray array = new JsonArray(); element.getAsJsonArray().forEach(value -> array.add(canonicalElement(value))); return array;
        }
        if (element.isJsonObject()) {
            JsonObject object = new JsonObject();
            element.getAsJsonObject().entrySet().stream().sorted(Comparator.comparing(java.util.Map.Entry::getKey))
                    .forEach(entry -> object.add(entry.getKey(), canonicalElement(entry.getValue())));
            return object;
        }
        return element.deepCopy();
    }

    private static IllegalArgumentException invalid(String detail) {
        return new IllegalArgumentException("INVALID_WISH_PROGRAM:" + detail);
    }
}
