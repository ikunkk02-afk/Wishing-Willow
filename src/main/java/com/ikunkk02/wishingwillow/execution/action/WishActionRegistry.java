package com.ikunkk02.wishingwillow.execution.action;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.ikunkk02.wishingwillow.ai.WishCapability;
import com.ikunkk02.wishingwillow.planning.WishActionType;
import com.ikunkk02.wishingwillow.research.RegistryEntryType;

import javax.annotation.Nullable;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Registry of explicit, typed Minecraft primitives. */
public final class WishActionRegistry {
    private static final Gson GSON = new Gson();
    private static final WishActionRegistry DEFAULT = createDefault();

    private final Map<String, WishActionDefinition> definitions;
    private final EnumMap<WishActionType, WishActionDefinition> byLegacyType;

    private WishActionRegistry(List<WishActionDefinition> definitions) {
        Map<String, WishActionDefinition> ids = new LinkedHashMap<>();
        EnumMap<WishActionType, WishActionDefinition> legacy = new EnumMap<>(WishActionType.class);
        for (WishActionDefinition definition : definitions) {
            if (ids.putIfAbsent(definition.id(), definition) != null) throw new IllegalArgumentException("DUPLICATE_ACTION_ID");
            if (definition.legacyType() != null && legacy.putIfAbsent(definition.legacyType(), definition) != null) {
                throw new IllegalArgumentException("DUPLICATE_ACTION_TYPE");
            }
        }
        this.definitions = Collections.unmodifiableMap(ids);
        this.byLegacyType = legacy;
    }

    public static WishActionRegistry defaults() { return DEFAULT; }
    public List<WishActionDefinition> definitions() { return List.copyOf(definitions.values()); }
    public Set<String> ids() { return definitions.keySet(); }
    @Nullable public WishActionDefinition find(String id) { return definitions.get(id); }
    @Nullable public WishActionDefinition definition(WishActionType type) { return byLegacyType.get(type); }

    /** Legacy executor access retained for old saved plans; new code uses definitions. */
    public boolean contains(WishActionType type) { return byLegacyType.containsKey(type); }
    public WishActionExecutor get(WishActionType type) {
        WishActionDefinition definition = byLegacyType.get(type);
        return definition == null ? null : definition.executor();
    }
    public Set<WishActionType> registered() { return Collections.unmodifiableSet(byLegacyType.keySet()); }

    /** Compact catalog included in the very first AI understanding request. */
    public JsonArray catalogJson() {
        JsonArray result = new JsonArray();
        definitions.values().forEach(definition -> {
            JsonObject value = new JsonObject();
            value.addProperty("id", definition.id());
            value.addProperty("description", definition.description());
            value.add("parameters", definition.parameterSchema());
            if (definition.resourceKind() != null) {
                value.addProperty("resource_kind", definition.resourceKind().name().toLowerCase(java.util.Locale.ROOT));
                value.addProperty("resource_parameter", definition.resourceParameter());
            }
            JsonArray capabilities = new JsonArray();
            definition.capabilities().stream().map(capability -> capability.name().toLowerCase(java.util.Locale.ROOT))
                    .sorted().forEach(capabilities::add);
            value.add("capabilities", capabilities);
            value.addProperty("timeout_ms", definition.timeout().toMillis());
            value.addProperty("result_type", definition.resultType());
            result.add(value);
        });
        return result;
    }

    public String catalogPrompt() { return GSON.toJson(catalogJson()); }

    /** Prompt-budget catalog: schemas remain authoritative in the independent JSON output contract. */
    public String summaryPrompt() {
        JsonArray result = new JsonArray();
        definitions.values().forEach(definition -> {
            JsonObject value = new JsonObject();
            value.addProperty("id", definition.id());
            String description = definition.description();
            int boundary = description.indexOf("\nDO NOT USE WHEN:");
            value.addProperty("capability", boundary < 0 ? description : description.substring(0, boundary));
            if (definition.resourceKind() != null) {
                value.addProperty("resource_kind", definition.resourceKind().name().toLowerCase(java.util.Locale.ROOT));
                value.addProperty("resource_parameter", definition.resourceParameter());
            }
            result.add(value);
        });
        return GSON.toJson(result);
    }

    private static WishActionRegistry createDefault() {
        List<WishActionDefinition> values = new ArrayList<>();
        add(values, "give_item", WishActionType.GIVE_ITEM, StandardWishActionExecutors.giveItem(), 2,
                Set.of(WishCapability.GIVE_ITEM, WishCapability.INVENTORY_CHANGE),
                schema(p("item", "string", true), p("count", "integer", true, 1d, 4096d),
                        p("target", "string", false, null, null, "self")),
                description("put a real item stack in the player's inventory", "a block must exist in the world or fall physically", "给我64颗钻石 | give me bread", "remove_item, place_block"));
        add(values, "remove_item", WishActionType.REMOVE_ITEM, StandardWishActionExecutors.removeItem(), 2,
                Set.of(WishCapability.REMOVE_ITEM, WishCapability.INVENTORY_CHANGE),
                schema(p("item", "string", true), p("count", "integer", true, 1d, 4096d)),
                description("remove a bounded item count from inventory", "removing world blocks", "拿走我10个石头", "give_item"));
        add(values, "apply_effect", WishActionType.APPLY_EFFECT, StandardWishActionExecutors.applyEffect(), 2,
                Set.of(WishCapability.POWER_BUFF, WishCapability.POWER_DEBUFF),
                schema(p("effect", "string", true), p("duration_seconds", "integer", false, 1d, 72000d),
                        p("amplifier", "integer", false, 0d, 255d)),
                description("apply one exact status effect", "the wish asks for an entire effect category", "让我速度5持续10分钟", "apply_effect_group, remove_effect"));
        add(values, "remove_effect", WishActionType.REMOVE_EFFECT, StandardWishActionExecutors.removeEffect(), 2,
                Set.of(WishCapability.POWER_DEBUFF), schema(p("effect", "string", true)),
                description("remove one exact status effect", "all effects must be cleared", "移除我的中毒", "clear_effects"));
        add(values, "clear_effects", WishActionType.CLEAR_EFFECTS, StandardWishActionExecutors.clearEffects(), 2,
                Set.of(WishCapability.POWER_DEBUFF), schema(),
                description("clear all active status effects", "only one effect should be removed", "清除我身上的所有效果", "remove_effect"));
        add(values, "apply_effect_group", WishActionType.APPLY_EFFECT_CATEGORY, StandardWishActionExecutors.applyEffectCategory(), 4,
                Set.of(WishCapability.POWER_BUFF, WishCapability.POWER_DEBUFF),
                schema(p("group", "string", true, null, null, "beneficial", "harmful"),
                        p("duration_seconds", "integer", false, 1d, 72000d),
                        p("amplifier", "integer", false, 0d, 255d)),
                description("apply every live-registry effect in beneficial or harmful group", "one named effect is requested", "给我所有正面效果 | all beneficial effects", "apply_effect"));
        add(values, "modify_health", WishActionType.MODIFY_HEALTH, StandardWishActionExecutors.modifyHealth(), 2,
                Set.of(WishCapability.HEALING, WishCapability.DAMAGE),
                schema(p("delta", "number", true, -20d, 20d), p("allow_lethal", "boolean", false)),
                description("change current health by a bounded amount", "persistent max health is requested", "恢复10点生命", "modify_attribute"));
        add(values, "modify_hunger", WishActionType.MODIFY_HUNGER, StandardWishActionExecutors.modifyHunger(), 2,
                Set.of(WishCapability.PLAYER_ATTRIBUTE), schema(p("delta", "integer", true, -20d, 20d)),
                description("change the player's food level", "a potion effect or inventory food is requested", "填满我的饥饿值", "give_item"));
        add(values, "modify_attribute", WishActionType.MODIFY_ATTRIBUTE, StandardWishActionExecutors.modifyAttribute(), 3,
                Set.of(WishCapability.PLAYER_ATTRIBUTE),
                schema(p("attribute", "string", true),
                        p("operation", "string", false, null, null, "add", "multiply_total"),
                        p("amount", "number", false, -100d, 100d),
                        p("duration_seconds", "integer", false, 1d, 72000d)),
                description("change a supported player attribute with a bounded lease", "a temporary status effect expresses the request", "提高我的最大生命值", "apply_effect"));
        add(values, "teleport_player", WishActionType.TELEPORT, StandardWishActionExecutors.teleport(), 5,
                Set.of(WishCapability.TELEPORT, WishCapability.DIMENSION_TRAVEL),
                schema(p("mode", "string", false, null, null, "nearby_safe", "candidate_dimension"),
                        p("distance_min", "integer", false, 1d, 256d),
                        p("distance_max", "integer", false, 1d, 256d),
                        p("dimension", "string", false)),
                description("teleport the player to validated coordinates or dimension", "only visual movement is requested", "把我传送到出生点", "place_pattern"));
        add(values, "place_block", WishActionType.CHANGE_BLOCK, StandardWishActionExecutors.changeBlock(), 3,
                Set.of(WishCapability.BLOCK_CHANGE),
                schema(p("block", "string", true), p("distance_min", "integer", false, 1d, 32d),
                        p("distance_max", "integer", false, 1d, 64d)),
                description("place one static block", "blocks must fall under gravity or many blocks form a pattern", "在我面前放一个金块", "place_pattern, spawn_falling_block"));
        add(values, "replace_blocks", WishActionType.REPLACE_BLOCK_AREA, StandardWishActionExecutors.replaceBlockArea(), 8,
                Set.of(WishCapability.BLOCK_CHANGE),
                schema(p("block", "string", true), p("radius", "integer", false, 1d, 16d),
                        p("max_blocks", "integer", false, 1d, 2048d)),
                description("replace a bounded area with one block type", "one block or a gravity delivery is requested", "把附近地面换成玻璃", "place_block, place_pattern"));
        add(values, "place_pattern", WishActionType.PLACE_BLOCK_PATTERN, StandardWishActionExecutors.placeBlockPattern(), 10,
                Set.of(WishCapability.BLOCK_CHANGE, WishCapability.STRUCTURE),
                schema(p("block", "string", true), p("pattern", "string", false, null, null, "enclosure", "pillar"),
                        p("count", "integer", false, 1d, 2048d), p("radius", "integer", false, 1d, 16d)),
                description("place multiple static blocks in a safe supported pattern", "blocks must physically fall", "用铁块围成一圈", "place_block, spawn_falling_block"));
        add(values, "create_structure", WishActionType.CREATE_STRUCTURE, StandardWishActionExecutors.createStructure(), 15,
                Set.of(WishCapability.STRUCTURE), schema(p("structure", "string", true)),
                description("create one supported bounded structure", "a static block or pattern is sufficient", "build a supported structure", "place_pattern"));
        add(values, "spawn_falling_block", WishActionType.FALLING_BLOCK_SHOWER, new FallingBlockShowerExecutor(), 30,
                Set.of(WishCapability.BLOCK_CHANGE, WishCapability.WORLD_EVENT),
                schema(p("block", "string", true),
                        p("target", "string", false, null, null, "self", "area"),
                        p("height", "integer", false, 8d, 64d),
                        p("horizontal_radius", "integer", false, 1d, 32d),
                        p("count", "integer", true, 1d, 256d),
                        p("interval_ticks", "integer", false, 1d, 20d),
                        p("landing", "string", false, null, null, "place", "drop_item", "place_or_drop", "deliver_to_player")),
                description("ONLY use resources registered as BLOCKS. Spawns real FallingBlockEntity objects; block MUST be a minecraft:block registry id such as minecraft:diamond_block, minecraft:gold_block, or minecraft:sand", "ordinary item resources such as minecraft:diamond, minecraft:apple, or minecraft:iron_ingot; use spawn_item_rain for those", "make 64 diamond blocks fall from the sky | rain sand blocks", "spawn_item_rain, place_block, give_item"));
        add(values, "spawn_item_rain", WishActionType.ITEM_RAIN, new ItemRainExecutor(), 60,
                Set.of(WishCapability.GIVE_ITEM),
                schema(p("item", "string", true),
                        p("count", "integer", true, 1d, 4096d),
                        p("target", "string", false, null, null, "self", "area"),
                        p("height", "integer", false, 8d, 64d),
                        p("horizontal_radius", "integer", false, 1d, 32d),
                        p("interval_ticks", "integer", false, 1d, 20d),
                        p("delivery", "string", false, null, null, "world_items", "deliver_to_player")),
                description("actual item resources physically falling from above as real ItemEntity objects; item MUST be a minecraft:item registry id such as minecraft:diamond, minecraft:apple, or minecraft:iron_ingot", "actual blocks that should fall as blocks; use spawn_falling_block for those", "make 64 diamonds fall from the sky as items | rain apples", "spawn_falling_block, give_item"));
        add(values, "spawn_entity", WishActionType.SPAWN_ENTITY, StandardWishActionExecutors.spawnEntity(), 5,
                Set.of(WishCapability.SPAWN_ENTITY, WishCapability.FRIENDLY_ENTITY, WishCapability.HOSTILE_ENTITY),
                schema(p("entity", "string", true), p("count", "integer", false, 1d, 64d),
                        p("distance_min", "integer", false, 1d, 64d), p("distance_max", "integer", false, 1d, 64d)),
                description("spawn one or more validated entity types", "a mod-specific AI behavior is required", "召唤10只鸡", "set_entity_target, follow_player"));
        add(values, "remove_entity", WishActionType.DESPAWN_ENTITY, StandardWishActionExecutors.despawnEntity(), 5,
                Set.of(WishCapability.SPAWN_ENTITY),
                schema(p("entity", "string", true), p("max_count", "integer", false, 1d, 64d),
                        p("radius", "number", false, 1d, 64d)),
                description("remove nearby entities matching a validated type", "items or blocks are targeted", "移除附近的僵尸", "spawn_entity"));
        add(values, "set_entity_target", WishActionType.CHANGE_MOB_TARGET, StandardWishActionExecutors.changeMobTarget(), 5,
                Set.of(WishCapability.MOB_BEHAVIOR),
                schema(pd("disposition", "string", "player", null, null,
                                "player", "nearest_hostile", "clear"),
                        pd("max_entities", "integer", 8, 1d, 32d),
                        pd("radius", "integer", 16, 2d, 64d)),
                description("set a generic mob target using vanilla behavior controls", "the request requires a third-party mod's unique AI implementation", "让附近僵尸攻击我", "follow_player, avoid_player"));
        add(values, "follow_player", WishActionType.FOLLOW_PLAYER, StandardWishActionExecutors.followPlayer(), 5,
                Set.of(WishCapability.PERSISTENT_FOLLOWER, WishCapability.MOB_BEHAVIOR),
                schema(pd("max_entities", "integer", 8, 1d, 32d),
                        pd("radius", "integer", 16, 2d, 64d),
                        pd("duration_seconds", "integer", 600, 1d, 2147483647d),
                        pd("permanent", "boolean", false, null, null)),
                description("make selected vanilla mobs follow the player", "a named mod-specific tracking AI must be preserved", "让狼一直跟着我", "set_entity_target, avoid_player"));
        add(values, "avoid_player", WishActionType.AVOID_PLAYER, StandardWishActionExecutors.avoidPlayer(), 5,
                Set.of(WishCapability.MOB_BEHAVIOR),
                schema(pd("max_entities", "integer", 8, 1d, 32d),
                        pd("radius", "integer", 16, 2d, 64d),
                        pd("duration_seconds", "integer", 600, 1d, 2147483647d),
                        pd("permanent", "boolean", false, null, null)),
                description("make selected mobs keep away from the player", "the entity should attack or follow", "make creepers avoid me", "follow_player, set_entity_target"));
        add(values, "modify_reputation", WishActionType.CHANGE_REPUTATION, StandardWishActionExecutors.changeReputation(), 3,
                Set.of(WishCapability.REPUTATION),
                schema(p("delta", "integer", true, -100d, 100d), p("radius", "number", false, 1d, 64d)),
                description("change a supported reputation value", "inventory or mob targeting is requested", "improve my local reputation", "set_entity_target"));
        add(values, "start_predefined_event", WishActionType.START_PREDEFINED_EVENT, StandardWishActionExecutors.predefinedEvent(), 5,
                Set.of(WishCapability.WORLD_EVENT), schema(p("event", "string", true)),
                description("start a verified Wishing Willow built-in event", "the event or mod behavior is not registered", "start a known built-in event", "spawn_entity, set_weather"));
        add(values, "set_weather", WishActionType.CHANGE_WEATHER, StandardWishActionExecutors.changeWeather(), 2,
                Set.of(WishCapability.CHANGE_WEATHER),
                schema(p("weather", "string", true, null, null, "clear", "rain", "thunder"),
                        p("duration_seconds", "integer", false, 1d, 72000d)),
                description("set clear, rain, or thunder weather", "only lightning at one location is requested", "change the weather to thunder", "spawn_lightning"));
        add(values, "set_time", WishActionType.CHANGE_TIME, StandardWishActionExecutors.changeTime(), 2,
                Set.of(WishCapability.CHANGE_TIME),
                schema(p("value", "string", true, null, null, "day", "noon", "night", "midnight", "dawn", "dusk")),
                description("set the world day time", "a delay in program execution is requested", "set the time to midnight | set noon", "delay"));
        add(values, "spawn_lightning", WishActionType.LIGHTNING, StandardWishActionExecutors.lightning(), 2,
                Set.of(WishCapability.LIGHTNING),
                schema(p("count", "integer", false, 1d, 64d), p("distance_min", "integer", false, 1d, 32d),
                        p("distance_max", "integer", false, 1d, 64d)),
                description("spawn a lightning bolt near the player as a real or presentation event", "persistent thunder weather is requested", "strike lightning to celebrate", "set_weather, play_sound"));
        add(values, "create_explosion", WishActionType.EXPLOSION, StandardWishActionExecutors.explosion(), 3,
                Set.of(WishCapability.EXPLOSION),
                schema(p("power", "number", false, 0.1d, 8d), p("destroy_blocks", "boolean", false),
                        p("distance_min", "integer", false, 1d, 64d), p("distance_max", "integer", false, 1d, 128d)),
                description("create one policy-bounded explosion", "a sound-only celebration is sufficient", "create a safe distant explosion", "play_sound, spawn_particle"));
        add(values, "play_sound", WishActionType.PLAY_SOUND, StandardWishActionExecutors.playSound(), 2,
                Set.of(WishCapability.SOUND_EVENT),
                schema(p("sound", "string", true), p("volume", "number", false, 0.1d, 2d),
                        p("pitch", "number", false, 0.1d, 2d), p("distance", "integer", false, 1d, 128d)),
                description("play a validated sound event", "a physical lightning bolt or explosion is required", "play a level-up sound", "spawn_particle, spawn_lightning"));
        add(values, "spawn_particle", WishActionType.SPAWN_PARTICLE, StandardWishActionExecutors.spawnParticle(), 2,
                Set.of(WishCapability.VISUAL_EVENT),
                schema(p("particle", "string", true), p("count", "integer", false, 1d, 512d),
                        p("radius", "number", false, 0d, 64d)),
                description("spawn validated presentation particles", "particles would substitute for a required physical outcome", "spawn a ring of firework particles", "play_sound"));
        add(values, "entity_attraction_aura", WishActionType.ENTITY_ATTRACTION_AURA, StandardWishActionExecutors.entityAttractionAura(), 3,
                Set.of(WishCapability.WORLD_EVENT),
                schema(p("radius", "number", false, 8d, 256d), p("strength", "number", false, 0.1d, 3d),
                        p("permanent", "boolean", false), p("include_hostile", "boolean", false),
                        p("include_passive", "boolean", false), p("include_villagers", "boolean", false),
                        p("include_modded", "boolean", false)),
                description("create a permanent or long-lasting attraction aura that pulls all nearby living entities toward the player",
                        "each entity should be handled individually by a follow action",
                        "make all nearby creatures gather around me forever", "follow_player, avoid_player"));

        addFlow(values, "repeat", 30, schema(p("count", "integer", true, 1d, 16d),
                        p("actions", "array", true)),
                description("repeat a bounded action composition; count is clamped by policy", "an unbounded loop or one action already has a count parameter", "repeat a sound three times", "sequence, parallel"));
        addFlow(values, "delay", 30, schema(p("ticks", "integer", true, 1d, 1200d)),
                description("wait a bounded number of ticks between sequential actions", "setting world time is requested", "wait 20 ticks before lightning", "sequence, set_time"));
        addFlow(values, "sequence", 60, schema(p("actions", "array", true)),
                description("execute child actions in order", "children are independent and should start together", "give an item and then strike lightning", "parallel, delay"));
        addFlow(values, "parallel", 60, schema(p("actions", "array", true)),
                description("execute a bounded set of independent child actions together", "child order matters", "play a sound and particles together", "sequence"));
        return new WishActionRegistry(values);
    }

    private static void add(List<WishActionDefinition> values, String id, WishActionType type,
                            WishActionExecutor executor, long timeoutSeconds, Set<WishCapability> capabilities,
                            JsonObject schema, String description) {
        ResourceMetadata resource = resource(type);
        values.add(new WishActionDefinition(id, description, schema, capabilities,
                resource == null ? null : resource.kind(), resource == null ? "" : resource.parameter(), type, executor,
                Duration.ofSeconds(timeoutSeconds), "ActionResult", false));
    }

    private static void addFlow(List<WishActionDefinition> values, String id, long timeoutSeconds,
                                JsonObject schema, String description) {
        values.add(new WishActionDefinition(id, description, schema, Set.of(), null, "", null, null,
                Duration.ofSeconds(timeoutSeconds), "ActionResult", true));
    }

    @Nullable
    private static ResourceMetadata resource(WishActionType type) {
        return switch (type) {
            case GIVE_ITEM, REMOVE_ITEM, ITEM_RAIN -> new ResourceMetadata(RegistryEntryType.ITEM, "item");
            case APPLY_EFFECT, REMOVE_EFFECT -> new ResourceMetadata(RegistryEntryType.EFFECT, "effect");
            case SPAWN_ENTITY, DESPAWN_ENTITY -> new ResourceMetadata(RegistryEntryType.ENTITY, "entity");
            case CHANGE_BLOCK, REPLACE_BLOCK_AREA, PLACE_BLOCK_PATTERN, FALLING_BLOCK_SHOWER ->
                    new ResourceMetadata(RegistryEntryType.BLOCK, "block");
            case PLAY_SOUND -> new ResourceMetadata(RegistryEntryType.SOUND, "sound");
            case SPAWN_PARTICLE -> new ResourceMetadata(RegistryEntryType.PARTICLE, "particle");
            case TELEPORT -> new ResourceMetadata(RegistryEntryType.DIMENSION, "dimension");
            default -> null;
        };
    }

    private record ResourceMetadata(RegistryEntryType kind, String parameter) { }

    private static String description(String use, String doNotUse, String examples, String related) {
        String safeExamples = examples.chars().allMatch(value -> value >= 32 && value < 127)
                ? examples : exampleFor(use);
        return "USE WHEN:\n- " + use + "\nDO NOT USE WHEN:\n- " + doNotUse
                + "\nEXAMPLES:\n- " + safeExamples + "\nRELATED ACTIONS:\n- " + related;
    }

    private static String exampleFor(String use) {
        if (use.contains("physically fall")) return "make 100 diamond blocks fall from the sky | rain gold blocks | drop sand from above";
        if (use.contains("beneficial")) return "give me all beneficial effects";
        if (use.contains("item stack")) return "give me 64 diamonds";
        if (use.contains("status effect")) return "give me Speed V for ten minutes";
        if (use.contains("entity types")) return "summon ten chickens";
        if (use.contains("thunder weather")) return "change the weather to thunder";
        if (use.contains("world day time")) return "set the time to midnight";
        if (use.contains("lightning bolt")) return "strike lightning to celebrate";
        return "a wish matching the USE WHEN conditions above";
    }

    private record Param(String name, String type, boolean required, Double min, Double max,
                         Object defaultValue, String... enums) { }

    private static Param p(String name, String type, boolean required) {
        return new Param(name, type, required, null, null, null);
    }

    private static Param p(String name, String type, boolean required, Double min, Double max,
                           String... enums) {
        return new Param(name, type, required, min, max, null, enums);
    }

    private static Param pd(String name, String type, Object defaultValue, Double min, Double max,
                            String... enums) {
        return new Param(name, type, false, min, max, defaultValue, enums);
    }

    /** Strict parameter boundary: every declared property is typed and bounded. */
    private static JsonObject schema(Param... params) {
        JsonObject root = new JsonObject();
        root.addProperty("type", "object");
        root.addProperty("additionalProperties", false);
        JsonObject properties = new JsonObject();
        JsonArray required = new JsonArray();
        for (Param param : params) {
            JsonObject property = new JsonObject();
            property.addProperty("type", param.type);
            if (param.min != null) property.addProperty("minimum", param.min);
            if (param.max != null) property.addProperty("maximum", param.max);
            if (param.defaultValue instanceof Number value) property.addProperty("default", value);
            else if (param.defaultValue instanceof Boolean value) property.addProperty("default", value);
            else if (param.defaultValue instanceof String value) property.addProperty("default", value);
            if (param.enums.length > 0) {
                JsonArray values = new JsonArray();
                for (String value : param.enums) values.add(value);
                property.add("enum", values);
            }
            properties.add(param.name, property);
            if (param.required) required.add(param.name);
        }
        root.add("properties", properties); root.add("required", required);
        return root;
    }
}
