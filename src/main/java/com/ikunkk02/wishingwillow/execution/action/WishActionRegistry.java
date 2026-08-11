package com.ikunkk02.wishingwillow.execution.action;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.ikunkk02.wishingwillow.ai.WishCapability;
import com.ikunkk02.wishingwillow.planning.WishActionType;

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
            JsonArray capabilities = new JsonArray();
            definition.capabilities().stream().map(Enum::name).sorted().forEach(capabilities::add);
            value.add("capabilities", capabilities);
            value.addProperty("timeout_ms", definition.timeout().toMillis());
            value.addProperty("result_type", definition.resultType());
            result.add(value);
        });
        return result;
    }

    public String catalogPrompt() { return GSON.toJson(catalogJson()); }

    private static WishActionRegistry createDefault() {
        List<WishActionDefinition> values = new ArrayList<>();
        add(values, "give_item", WishActionType.GIVE_ITEM, StandardWishActionExecutors.giveItem(), 2,
                Set.of(WishCapability.GIVE_ITEM, WishCapability.INVENTORY_CHANGE),
                schema("item", "string", "count", "integer", "target", "string"),
                description("put a real item stack in the player's inventory", "a block must exist in the world or fall physically", "给我64颗钻石 | give me bread", "remove_item, place_block"));
        add(values, "remove_item", WishActionType.REMOVE_ITEM, StandardWishActionExecutors.removeItem(), 2,
                Set.of(WishCapability.REMOVE_ITEM, WishCapability.INVENTORY_CHANGE), schema("item", "string", "count", "integer"),
                description("remove a bounded item count from inventory", "removing world blocks", "拿走我10个石头", "give_item"));
        add(values, "apply_effect", WishActionType.APPLY_EFFECT, StandardWishActionExecutors.applyEffect(), 2,
                Set.of(WishCapability.POWER_BUFF, WishCapability.POWER_DEBUFF), schema("effect", "string", "duration_seconds", "integer", "amplifier", "integer"),
                description("apply one exact status effect", "the wish asks for an entire effect category", "让我速度5持续10分钟", "apply_effect_group, remove_effect"));
        add(values, "remove_effect", WishActionType.REMOVE_EFFECT, StandardWishActionExecutors.removeEffect(), 2,
                Set.of(WishCapability.POWER_DEBUFF), schema("effect", "string"),
                description("remove one exact status effect", "all effects must be cleared", "移除我的中毒", "clear_effects"));
        add(values, "clear_effects", WishActionType.CLEAR_EFFECTS, StandardWishActionExecutors.clearEffects(), 2,
                Set.of(WishCapability.POWER_DEBUFF), schema(),
                description("clear all active status effects", "only one effect should be removed", "清除我身上的所有效果", "remove_effect"));
        add(values, "apply_effect_group", WishActionType.APPLY_EFFECT_CATEGORY, StandardWishActionExecutors.applyEffectCategory(), 4,
                Set.of(WishCapability.POWER_BUFF, WishCapability.POWER_DEBUFF), schema("group", "string", "duration_seconds", "integer", "amplifier", "integer"),
                description("apply every live-registry effect in BENEFICIAL or HARMFUL group", "one named effect is requested", "给我所有正面效果 | all beneficial effects", "apply_effect"));
        add(values, "modify_health", WishActionType.MODIFY_HEALTH, StandardWishActionExecutors.modifyHealth(), 2,
                Set.of(WishCapability.HEALING, WishCapability.DAMAGE), schema("delta", "number", "allow_lethal", "boolean"),
                description("change current health by a bounded amount", "persistent max health is requested", "恢复10点生命", "modify_attribute"));
        add(values, "modify_hunger", WishActionType.MODIFY_HUNGER, StandardWishActionExecutors.modifyHunger(), 2,
                Set.of(WishCapability.PLAYER_ATTRIBUTE), schema("delta", "integer"),
                description("change the player's food level", "a potion effect or inventory food is requested", "填满我的饥饿值", "give_item"));
        add(values, "modify_attribute", WishActionType.MODIFY_ATTRIBUTE, StandardWishActionExecutors.modifyAttribute(), 3,
                Set.of(WishCapability.PLAYER_ATTRIBUTE), schema("attribute", "string", "operation", "string", "amount", "number", "duration_seconds", "integer"),
                description("change a supported player attribute with a bounded lease", "a temporary status effect expresses the request", "提高我的最大生命值", "apply_effect"));
        add(values, "teleport_player", WishActionType.TELEPORT, StandardWishActionExecutors.teleport(), 5,
                Set.of(WishCapability.TELEPORT, WishCapability.DIMENSION_TRAVEL), schema("mode", "string", "distance_min", "integer", "distance_max", "integer", "dimension", "string"),
                description("teleport the player to validated coordinates or dimension", "only visual movement is requested", "把我传送到出生点", "place_pattern"));
        add(values, "place_block", WishActionType.CHANGE_BLOCK, StandardWishActionExecutors.changeBlock(), 3,
                Set.of(WishCapability.BLOCK_CHANGE), schema("block", "string", "distance_min", "integer", "distance_max", "integer"),
                description("place one static block", "blocks must fall under gravity or many blocks form a pattern", "在我面前放一个金块", "place_pattern, spawn_falling_block"));
        add(values, "replace_blocks", WishActionType.REPLACE_BLOCK_AREA, StandardWishActionExecutors.replaceBlockArea(), 8,
                Set.of(WishCapability.BLOCK_CHANGE), schema("block", "string", "radius", "integer", "max_blocks", "integer"),
                description("replace a bounded area with one block type", "one block or a gravity delivery is requested", "把附近地面换成玻璃", "place_block, place_pattern"));
        add(values, "place_pattern", WishActionType.PLACE_BLOCK_PATTERN, StandardWishActionExecutors.placeBlockPattern(), 10,
                Set.of(WishCapability.BLOCK_CHANGE, WishCapability.STRUCTURE), schema("block", "string", "pattern", "string", "count", "integer", "radius", "integer"),
                description("place multiple static blocks in a safe supported pattern", "blocks must physically fall", "用铁块围成一圈", "place_block, spawn_falling_block"));
        add(values, "create_structure", WishActionType.CREATE_STRUCTURE, StandardWishActionExecutors.createStructure(), 15,
                Set.of(WishCapability.STRUCTURE), schema("structure", "string", "radius", "integer"),
                description("create one supported bounded structure", "a static block or pattern is sufficient", "build a supported structure", "place_pattern"));
        add(values, "spawn_falling_block", WishActionType.FALLING_BLOCK_SHOWER, new FallingBlockShowerExecutor(), 30,
                Set.of(WishCapability.BLOCK_CHANGE, WishCapability.WORLD_EVENT), schema("block", "string", "target", "string", "height", "integer", "horizontal_radius", "integer", "count", "integer", "interval_ticks", "integer", "landing", "string"),
                description("blocks must physically fall under gravity; block rain; drop blocks from above a player/location", "the player only wants an inventory item or static placement", "让100个钻石块从天而降 | 天空下金块雨 | 让沙子从头顶砸下来", "place_block, give_item"));
        add(values, "spawn_entity", WishActionType.SPAWN_ENTITY, StandardWishActionExecutors.spawnEntity(), 5,
                Set.of(WishCapability.SPAWN_ENTITY, WishCapability.FRIENDLY_ENTITY, WishCapability.HOSTILE_ENTITY), schema("entity", "string", "count", "integer", "distance_min", "integer", "distance_max", "integer"),
                description("spawn one or more validated entity types", "a mod-specific AI behavior is required", "召唤10只鸡", "set_entity_target, follow_player"));
        add(values, "remove_entity", WishActionType.DESPAWN_ENTITY, StandardWishActionExecutors.despawnEntity(), 5,
                Set.of(WishCapability.SPAWN_ENTITY), schema("entity", "string", "max_count", "integer", "radius", "number"),
                description("remove nearby entities matching a validated type", "items or blocks are targeted", "移除附近的僵尸", "spawn_entity"));
        add(values, "set_entity_target", WishActionType.CHANGE_MOB_TARGET, StandardWishActionExecutors.changeMobTarget(), 5,
                Set.of(WishCapability.MOB_BEHAVIOR), schema("disposition", "string", "max_entities", "integer", "radius", "number"),
                description("set a generic mob target using vanilla behavior controls", "the request requires a third-party mod's unique AI implementation", "让附近僵尸攻击我", "follow_player, avoid_player"));
        add(values, "follow_player", WishActionType.FOLLOW_PLAYER, StandardWishActionExecutors.followPlayer(), 5,
                Set.of(WishCapability.PERSISTENT_FOLLOWER, WishCapability.MOB_BEHAVIOR), schema("max_entities", "integer", "radius", "number", "duration_seconds", "integer"),
                description("make selected vanilla mobs follow the player", "a named mod-specific tracking AI must be preserved", "让狼一直跟着我", "set_entity_target, avoid_player"));
        add(values, "avoid_player", WishActionType.AVOID_PLAYER, StandardWishActionExecutors.avoidPlayer(), 5,
                Set.of(WishCapability.MOB_BEHAVIOR), schema("max_entities", "integer", "radius", "number", "duration_seconds", "integer"),
                description("make selected mobs keep away from the player", "the entity should attack or follow", "make creepers avoid me", "follow_player, set_entity_target"));
        add(values, "modify_reputation", WishActionType.CHANGE_REPUTATION, StandardWishActionExecutors.changeReputation(), 3,
                Set.of(WishCapability.REPUTATION), schema("delta", "integer", "radius", "number"),
                description("change a supported reputation value", "inventory or mob targeting is requested", "improve my local reputation", "set_entity_target"));
        add(values, "start_predefined_event", WishActionType.START_PREDEFINED_EVENT, StandardWishActionExecutors.predefinedEvent(), 5,
                Set.of(WishCapability.WORLD_EVENT), schema("event", "string", "duration_seconds", "integer"),
                description("start a verified Wishing Willow built-in event", "the event or mod behavior is not registered", "start a known built-in event", "spawn_entity, set_weather"));
        add(values, "set_weather", WishActionType.CHANGE_WEATHER, StandardWishActionExecutors.changeWeather(), 2,
                Set.of(WishCapability.CHANGE_WEATHER), schema("weather", "string", "duration_seconds", "integer"),
                description("set clear, rain, or thunder weather", "only lightning at one location is requested", "change the weather to thunder", "spawn_lightning"));
        add(values, "set_time", WishActionType.CHANGE_TIME, StandardWishActionExecutors.changeTime(), 2,
                Set.of(WishCapability.CHANGE_TIME), schema("value", "string"),
                description("set the world day time", "a delay in program execution is requested", "set the time to midnight | set noon", "delay"));
        add(values, "spawn_lightning", WishActionType.LIGHTNING, StandardWishActionExecutors.lightning(), 2,
                Set.of(WishCapability.LIGHTNING), schema("count", "integer", "distance_min", "integer", "distance_max", "integer"),
                description("spawn a lightning bolt near the player as a real or presentation event", "persistent thunder weather is requested", "strike lightning to celebrate", "set_weather, play_sound"));
        add(values, "create_explosion", WishActionType.EXPLOSION, StandardWishActionExecutors.explosion(), 3,
                Set.of(WishCapability.EXPLOSION), schema("power", "number", "destroy_blocks", "boolean", "distance_min", "integer", "distance_max", "integer"),
                description("create one policy-bounded explosion", "a sound-only celebration is sufficient", "create a safe distant explosion", "play_sound, spawn_particle"));
        add(values, "play_sound", WishActionType.PLAY_SOUND, StandardWishActionExecutors.playSound(), 2,
                Set.of(WishCapability.SOUND_EVENT), schema("sound", "string", "volume", "number", "pitch", "number", "distance", "integer"),
                description("play a validated sound event", "a physical lightning bolt or explosion is required", "play a level-up sound", "spawn_particle, spawn_lightning"));
        add(values, "spawn_particle", WishActionType.SPAWN_PARTICLE, StandardWishActionExecutors.spawnParticle(), 2,
                Set.of(WishCapability.VISUAL_EVENT), schema("particle", "string", "count", "integer", "radius", "number"),
                description("spawn validated presentation particles", "particles would substitute for a required physical outcome", "spawn a ring of firework particles", "play_sound"));
        addFlow(values, "repeat", 30, schema("count", "integer", "actions", "array"),
                description("repeat a bounded action composition; count is clamped by policy", "an unbounded loop or one action already has a count parameter", "repeat a sound three times", "sequence, parallel"));
        addFlow(values, "delay", 30, schema("ticks", "integer"),
                description("wait a bounded number of ticks between sequential actions", "setting world time is requested", "wait 20 ticks before lightning", "sequence, set_time"));
        addFlow(values, "sequence", 60, schema("actions", "array"),
                description("execute child actions in order", "children are independent and should start together", "give an item and then strike lightning", "parallel, delay"));
        addFlow(values, "parallel", 60, schema("actions", "array"),
                description("execute a bounded set of independent child actions together", "child order matters", "play a sound and particles together", "sequence"));
        return new WishActionRegistry(values);
    }

    private static void add(List<WishActionDefinition> values, String id, WishActionType type,
                            WishActionExecutor executor, long timeoutSeconds, Set<WishCapability> capabilities,
                            JsonObject schema, String description) {
        values.add(new WishActionDefinition(id, description, schema, capabilities, type, executor,
                Duration.ofSeconds(timeoutSeconds), "ActionResult", false));
    }

    private static void addFlow(List<WishActionDefinition> values, String id, long timeoutSeconds,
                                JsonObject schema, String description) {
        values.add(new WishActionDefinition(id, description, schema, Set.of(), null, null,
                Duration.ofSeconds(timeoutSeconds), "ActionResult", true));
    }

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

    private static JsonObject schema(String... pairs) {
        JsonObject root = new JsonObject();
        root.addProperty("type", "object");
        root.addProperty("additionalProperties", false);
        JsonObject properties = new JsonObject();
        JsonArray required = new JsonArray();
        for (int index = 0; index < pairs.length; index += 2) {
            JsonObject property = new JsonObject(); property.addProperty("type", pairs[index + 1]);
            properties.add(pairs[index], property); required.add(pairs[index]);
        }
        root.add("properties", properties); root.add("required", required);
        return root;
    }
}
