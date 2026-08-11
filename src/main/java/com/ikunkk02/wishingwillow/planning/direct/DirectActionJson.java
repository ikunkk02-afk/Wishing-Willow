package com.ikunkk02.wishingwillow.planning.direct;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.internal.Streams;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.ikunkk02.wishingwillow.ai.WishInterpretation;
import com.ikunkk02.wishingwillow.ai.WishInterpretationValidator;
import com.ikunkk02.wishingwillow.execution.ExecutionSettingsSnapshot;
import com.ikunkk02.wishingwillow.planning.WishActionType;
import com.ikunkk02.wishingwillow.planning.WishExecutionRoute;
import com.ikunkk02.wishingwillow.research.RegistryEntryType;
import com.ikunkk02.wishingwillow.research.registry.RegistrySnapshot;

import java.io.StringReader;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

final class DirectActionJson {
    static final int MAX_JSON = 64 * 1024;
    static final Set<WishActionType> DIRECT_ACTIONS = Set.copyOf(EnumSet.of(
            WishActionType.GIVE_ITEM, WishActionType.REMOVE_ITEM,
            WishActionType.APPLY_EFFECT, WishActionType.REMOVE_EFFECT,
            WishActionType.CLEAR_EFFECTS, WishActionType.APPLY_EFFECT_CATEGORY,
            WishActionType.SPAWN_ENTITY, WishActionType.DESPAWN_ENTITY,
            WishActionType.TELEPORT, WishActionType.CHANGE_TIME, WishActionType.CHANGE_WEATHER,
            WishActionType.LIGHTNING, WishActionType.EXPLOSION,
            WishActionType.PLACE_BLOCK_PATTERN, WishActionType.REPLACE_BLOCK_AREA,
            WishActionType.PLAY_SOUND, WishActionType.SPAWN_PARTICLE,
            WishActionType.MODIFY_ATTRIBUTE, WishActionType.CHANGE_REPUTATION,
            WishActionType.START_PREDEFINED_EVENT
    ));
    private static final Set<String> ROOT_FIELDS = Set.of("route", "summary", "actions", "absurdity");
    private static final Set<String> ABSURDITY_FIELDS = Set.of("style", "intensity", "modifiers");
    private static final Set<String> ACTION_FIELDS = Set.of("type", "target", "resource", "parameters");
    private static final Set<String> PARAMETER_FIELDS = Set.of(
            "count", "duration_seconds", "amplifier", "category", "radius", "max_count",
            "distance_min", "distance_max", "mode", "value", "weather", "volume", "pitch",
            "distance", "power", "destroy_blocks", "max_blocks", "pattern", "attribute",
            "operation", "amount", "max_entities", "delta", "intensity", "template",
            "disposition"
    );
    private static final Gson GSON = new Gson();

    private DirectActionJson() {}

    static DirectActionPlan parse(String raw) {
        if (raw == null || raw.length() > MAX_JSON) throw invalid("INVALID_JSON");
        JsonElement parsed = parseStrict(stripFence(raw));
        if (!parsed.isJsonObject() || !parsed.getAsJsonObject().keySet().equals(ROOT_FIELDS)) {
            throw invalid("INVALID_JSON");
        }
        JsonObject root = parsed.getAsJsonObject();
        WishExecutionRoute route = enumValue(root, "route", WishExecutionRoute.class);
        String summary = string(root, "summary", 512, false);
        List<DirectWishAction> actions = actionArray(root, "actions", 10);
        JsonObject absurdity = object(root, "absurdity", ABSURDITY_FIELDS);
        WishAbsurdityStyle style = enumValue(absurdity, "style", WishAbsurdityStyle.class);
        int intensity = integer(absurdity, "intensity");
        if (intensity < 0 || intensity > 100) throw invalid("INVALID_ABSURDITY_INTENSITY");
        List<DirectWishAction> modifiers = actionArray(absurdity, "modifiers", 3);
        return new DirectActionPlan(route, summary, actions,
                new WishAbsurdityProfile(style, intensity, modifiers));
    }

    static JsonObject schema() {
        JsonObject root = JsonParser.parseString("""
                {"type":"object","additionalProperties":false,
                 "required":["route","summary","actions","absurdity"],
                 "properties":{
                   "route":{"type":"string","enum":["DIRECT_ACTION","COMPLEX_AGENT"]},
                   "summary":{"type":"string","minLength":1,"maxLength":512},
                   "actions":{"type":"array","minItems":0,"maxItems":10},
                   "absurdity":{"type":"object","additionalProperties":false,
                     "required":["style","intensity","modifiers"],"properties":{
                       "style":{"type":"string"},"intensity":{"type":"integer","minimum":0,"maximum":100},
                       "modifiers":{"type":"array","minItems":0,"maxItems":3}
                     }}
                 }}
                """).getAsJsonObject();
        JsonObject action = actionSchema();
        root.getAsJsonObject("properties").getAsJsonObject("actions").add("items", action.deepCopy());
        JsonObject absurdity = root.getAsJsonObject("properties").getAsJsonObject("absurdity")
                .getAsJsonObject("properties");
        absurdity.getAsJsonObject("modifiers").add("items", action.deepCopy());
        enumValues(absurdity.getAsJsonObject("style"), WishAbsurdityStyle.values());
        return root;
    }

    static String systemPrompt() {
        return """
                You are the Wishing Willow Direct Action Planner. Return only the strict JSON schema.

                THE WISH MUST COME TRUE FIRST.
                ABSURDITY MAKES THE FULFILLMENT STRANGER, NOT LESS TRUE.

                IF the wish can be represented by the allowlisted Action DSL, route DIRECT_ACTION.
                IF it requires an unknown mod API, special mod behavior, or capability research, route COMPLEX_AGENT
                with an empty actions array. A formatting problem is never a reason to choose COMPLEX_AGENT.

                Put every action required to make the core outcome true in actions. Then add 1-3 optional modifiers.
                Prefer particles, sounds, and theatrical presentation. Never replace the requested item, state,
                entity, weather, time, or destination with a joke. Never invent a registry ID.
                APPLY_EFFECT_CATEGORY with category BENEFICIAL represents every beneficial effect in the live server
                registry and must be used for all-positive-effects wishes. Do not enumerate effect IDs for that wish.
                Use amplifier 4 for a player-facing level 5 status effect because Minecraft amplifiers are zero-based.
                Use target SELF for player actions and WORLD for time/weather. Default absurdity intensity is 75-90.
                Modifiers must obey the same budgets as core actions. Prefer harmless presentation at intensity 100.

                Never output commands, command fields, Java, code, NBT, scripts, /op, /stop, /execute, /data,
                /function, shell text, or arbitrary executable strings. The only possible world operations are the
                allowlisted Action DSL types in the schema.
                """;
    }

    static String userMessage(String originalWish, WishInterpretation interpretation,
                              RegistrySnapshot registry, ExecutionSettingsSnapshot settings) {
        JsonObject root = new JsonObject();
        root.addProperty("original_wish_untrusted", clean(originalWish, 512));
        root.add("interpretation", JsonParser.parseString(WishInterpretationValidator.toJson(interpretation)));
        root.add("verified_registry_hints", registryHints(registry));
        JsonObject policy = new JsonObject();
        policy.addProperty("third_party_entities", settings.thirdPartyEntities());
        policy.addProperty("block_modification", settings.blockModification() && !settings.debugSafeMode());
        policy.addProperty("explosions", settings.explosions());
        policy.addProperty("destructive_explosions", settings.destructiveExplosions() && !settings.debugSafeMode());
        policy.addProperty("cross_dimension_teleport", settings.crossDimensionTeleport());
        policy.addProperty("debug_safe_mode", settings.debugSafeMode());
        policy.addProperty("maximum_destructive_severity", settings.maximumDestructiveSeverity());
        root.add("server_execution_policy", policy);
        return "<UNTRUSTED_DIRECT_ACTION_INPUT>\n" + GSON.toJson(root)
                + "\n</UNTRUSTED_DIRECT_ACTION_INPUT>";
    }

    static String repairMessage(String originalWish, WishInterpretation interpretation,
                                RegistrySnapshot registry, ExecutionSettingsSnapshot settings,
                                String error, String invalid) {
        JsonObject repair = new JsonObject();
        repair.addProperty("validation_error", clean(error, 96));
        repair.addProperty("invalid_response_untrusted", clean(invalid, 8192));
        return userMessage(originalWish, interpretation, registry, settings)
                + "\n<REPAIR_THIS_RESPONSE_ONCE>\n" + GSON.toJson(repair)
                + "\n</REPAIR_THIS_RESPONSE_ONCE>";
    }

    private static JsonObject actionSchema() {
        JsonObject action = JsonParser.parseString("""
                {"type":"object","additionalProperties":false,
                 "required":["type","target","resource","parameters"],"properties":{
                   "type":{"type":"string"},"target":{"type":"string"},
                   "resource":{"type":"string","maxLength":128},
                   "parameters":{"type":"object","additionalProperties":false,"properties":{
                     "count":{"type":"integer"},"duration_seconds":{"type":"integer"},
                     "amplifier":{"type":"integer"},"category":{"type":"string"},
                     "radius":{"type":"number"},"max_count":{"type":"integer"},
                     "distance_min":{"type":"integer"},"distance_max":{"type":"integer"},
                     "mode":{"type":"string"},"value":{"type":"string"},"weather":{"type":"string"},
                     "volume":{"type":"number"},"pitch":{"type":"number"},"distance":{"type":"integer"},
                     "power":{"type":"number"},"destroy_blocks":{"type":"boolean"},
                     "max_blocks":{"type":"integer"},"pattern":{"type":"string"},
                     "attribute":{"type":"string"},"operation":{"type":"string"},"amount":{"type":"number"},
                     "max_entities":{"type":"integer"},"delta":{"type":"integer"},
                     "intensity":{"type":"integer"},"template":{"type":"string"},"disposition":{"type":"string"}
                   }}
                 }}
                """).getAsJsonObject();
        JsonObject properties = action.getAsJsonObject("properties");
        enumValues(properties.getAsJsonObject("type"), DIRECT_ACTIONS.toArray(WishActionType[]::new));
        enumValues(properties.getAsJsonObject("target"), DirectWishTarget.values());
        return action;
    }

    private static List<DirectWishAction> actionArray(JsonObject owner, String name, int max) {
        JsonElement element = owner.get(name);
        if (element == null || !element.isJsonArray() || element.getAsJsonArray().size() > max) {
            throw invalid("INVALID_ACTION_ARRAY");
        }
        java.util.ArrayList<DirectWishAction> result = new java.util.ArrayList<>();
        for (JsonElement value : element.getAsJsonArray()) {
            if (!value.isJsonObject() || !value.getAsJsonObject().keySet().equals(ACTION_FIELDS)) {
                throw invalid("INVALID_ACTION_FIELDS");
            }
            JsonObject action = value.getAsJsonObject();
            WishActionType type = enumValue(action, "type", WishActionType.class);
            if (!DIRECT_ACTIONS.contains(type)) throw invalid("UNSUPPORTED_ACTION");
            DirectWishTarget target = enumValue(action, "target", DirectWishTarget.class);
            String resource = string(action, "resource", 128, true);
            JsonObject parameters = object(action, "parameters", null);
            if (!PARAMETER_FIELDS.containsAll(parameters.keySet())) throw invalid("INVALID_ACTION_PARAMETERS");
            result.add(new DirectWishAction(type, target, resource, parameters));
        }
        return List.copyOf(result);
    }

    private static JsonObject registryHints(RegistrySnapshot registry) {
        JsonObject result = new JsonObject();
        for (RegistryEntryType type : List.of(RegistryEntryType.ITEM, RegistryEntryType.EFFECT,
                RegistryEntryType.ENTITY, RegistryEntryType.BLOCK, RegistryEntryType.SOUND,
                RegistryEntryType.PARTICLE, RegistryEntryType.DIMENSION)) {
            JsonArray values = new JsonArray();
            common(type).stream().filter(id -> registry.contains(type, id)).forEach(values::add);
            registry.entries().getOrDefault(type, List.of()).stream().limit(12)
                    .filter(id -> !contains(values, id)).forEach(values::add);
            result.add(type.name(), values);
        }
        return result;
    }

    private static List<String> common(RegistryEntryType type) {
        return switch (type) {
            case ITEM -> List.of("minecraft:diamond", "minecraft:gold_ingot", "minecraft:bread");
            case EFFECT -> List.of("minecraft:speed", "minecraft:strength", "minecraft:night_vision");
            case ENTITY -> List.of("minecraft:zombie", "minecraft:chicken", "minecraft:wolf");
            case BLOCK -> List.of("minecraft:diamond_block", "minecraft:gold_block", "minecraft:stone");
            case SOUND -> List.of("minecraft:ui.toast.challenge_complete", "minecraft:entity.lightning_bolt.thunder",
                    "minecraft:entity.player.levelup");
            case PARTICLE -> List.of("minecraft:totem_of_undying", "minecraft:end_rod", "minecraft:enchanted_hit");
            case DIMENSION -> List.of("minecraft:overworld", "minecraft:the_nether", "minecraft:the_end");
            default -> List.of();
        };
    }

    private static boolean contains(JsonArray values, String expected) {
        for (JsonElement value : values) if (value.getAsString().equals(expected)) return true;
        return false;
    }

    private static JsonObject object(JsonObject owner, String name, Set<String> exactFields) {
        JsonElement element = owner.get(name);
        if (element == null || !element.isJsonObject()) throw invalid("INVALID_OBJECT");
        JsonObject object = element.getAsJsonObject();
        if (exactFields != null && !object.keySet().equals(exactFields)) throw invalid("INVALID_OBJECT_FIELDS");
        return object;
    }

    private static int integer(JsonObject owner, String name) {
        JsonElement value = owner.get(name);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()
                || !value.getAsString().matches("-?(0|[1-9][0-9]*)")) throw invalid("INVALID_INTEGER");
        try { return value.getAsInt(); } catch (RuntimeException error) { throw invalid("INVALID_INTEGER"); }
    }

    private static String string(JsonObject owner, String name, int max, boolean allowEmpty) {
        JsonElement value = owner.get(name);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw invalid("INVALID_STRING");
        }
        String text = value.getAsString().strip();
        if ((!allowEmpty && text.isEmpty()) || text.length() > max) throw invalid("INVALID_STRING");
        return text;
    }

    private static <E extends Enum<E>> E enumValue(JsonObject owner, String name, Class<E> type) {
        try { return Enum.valueOf(type, string(owner, name, 64, false)); }
        catch (IllegalArgumentException error) { throw invalid("INVALID_ENUM"); }
    }

    private static void enumValues(JsonObject property, Enum<?>[] values) {
        JsonArray array = new JsonArray();
        java.util.Arrays.stream(values).map(Enum::name).sorted().forEach(array::add);
        property.add("enum", array);
    }

    private static String stripFence(String raw) {
        String value = raw.strip();
        if (!value.startsWith("```")) return value;
        int newline = value.indexOf('\n');
        if (newline < 0 || !value.endsWith("```")) throw invalid("INVALID_JSON");
        return value.substring(newline + 1, value.length() - 3).strip();
    }

    private static JsonElement parseStrict(String json) {
        try {
            JsonReader reader = new JsonReader(new StringReader(json)); reader.setLenient(false);
            JsonElement value = Streams.parse(reader);
            if (reader.peek() != JsonToken.END_DOCUMENT) throw invalid("INVALID_JSON");
            return value;
        } catch (RuntimeException error) {
            if (error instanceof IllegalArgumentException illegal) throw illegal;
            throw invalid("INVALID_JSON");
        } catch (Exception error) {
            throw invalid("INVALID_JSON");
        }
    }

    private static String clean(String value, int max) {
        String clean = value == null ? "" : value.replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", " ").strip();
        return clean.length() <= max ? clean : clean.substring(0, max);
    }

    private static IllegalArgumentException invalid(String code) {
        return new IllegalArgumentException(code);
    }
}
