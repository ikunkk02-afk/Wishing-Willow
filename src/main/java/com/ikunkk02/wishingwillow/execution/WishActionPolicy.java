package com.ikunkk02.wishingwillow.execution;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.ikunkk02.wishingwillow.WishingWillow;
import com.ikunkk02.wishingwillow.ai.WishCapability;
import com.ikunkk02.wishingwillow.execution.action.WishActionDefinition;
import com.ikunkk02.wishingwillow.execution.action.WishActionRegistry;
import com.ikunkk02.wishingwillow.planning.CandidateReference;
import com.ikunkk02.wishingwillow.planning.CandidateSourceKind;
import com.ikunkk02.wishingwillow.planning.CapabilityRelationGraph;
import com.ikunkk02.wishingwillow.planning.MatchType;
import com.ikunkk02.wishingwillow.planning.WishActionType;
import com.ikunkk02.wishingwillow.planning.WishPlanBudget;
import com.ikunkk02.wishingwillow.planning.WishStepTiming;
import com.ikunkk02.wishingwillow.planning.WishTargetType;
import com.ikunkk02.wishingwillow.planning.WishTriggerType;
import com.ikunkk02.wishingwillow.planning.WishingWillowBuiltinCapability;
import com.ikunkk02.wishingwillow.research.RegistryEntryType;

import javax.annotation.Nullable;
import java.util.Set;

public final class WishActionPolicy {
    private WishActionPolicy() { }

    public static WishPolicyDecision validate(CandidateReference candidate, WishActionType action,
                                              JsonObject parameters, WishTargetType target,
                                              WishStepTiming timing, int delaySeconds,
                                              WishTriggerType trigger, int severity) {
        try {
            validateTiming(timing, delaySeconds, trigger);
            validateReference(candidate, action, parameters);
            validateParameters(action, parameters, target, severity, candidate);
            return WishPolicyDecision.allow();
        } catch (Rejected rejected) {
            return WishPolicyDecision.reject(rejected.error, rejected.getMessage());
        } catch (RuntimeException invalid) {
            return WishPolicyDecision.reject(WishExecutionAcceptError.INVALID_PARAMETER,
                    "Malformed parameters for " + action);
        }
    }

    @Nullable
    public static RegistryEntryType expectedResource(WishActionType action) {
        return switch (action) {
            case GIVE_ITEM, REMOVE_ITEM, ITEM_RAIN -> RegistryEntryType.ITEM;
            case SPAWN_ENTITY, DESPAWN_ENTITY, CHANGE_MOB_TARGET, FOLLOW_PLAYER, AVOID_PLAYER -> RegistryEntryType.ENTITY;
            case APPLY_EFFECT, REMOVE_EFFECT -> RegistryEntryType.EFFECT;
            case PLAY_SOUND -> RegistryEntryType.SOUND;
            case SPAWN_PARTICLE -> RegistryEntryType.PARTICLE;
            case CHANGE_BLOCK, REPLACE_BLOCK_AREA, PLACE_BLOCK_PATTERN, FALLING_BLOCK_SHOWER -> RegistryEntryType.BLOCK;
            default -> null;
        };
    }

    public static boolean supports(WishCapability requested, WishCapability provided, WishActionType action) {
        if (new CapabilityRelationGraph().relation(requested, provided) == MatchType.UNSATISFIED) return false;
        return switch (action) {
            case GIVE_ITEM -> Set.of(WishCapability.GIVE_ITEM, WishCapability.STRONG_WEAPON,
                    WishCapability.INVENTORY_CHANGE).contains(provided);
            case REMOVE_ITEM -> Set.of(WishCapability.REMOVE_ITEM, WishCapability.INVENTORY_CHANGE).contains(provided);
            case ITEM_RAIN -> provided == WishCapability.GIVE_ITEM
                    || provided == WishCapability.INVENTORY_CHANGE
                    || provided == WishCapability.WORLD_EVENT;
            case SPAWN_ENTITY -> Set.of(WishCapability.SPAWN_ENTITY,
                    WishCapability.HOSTILE_ENTITY, WishCapability.FRIENDLY_ENTITY,
                    WishCapability.STALKING_ENTITY, WishCapability.PERSISTENT_FOLLOWER,
                    WishCapability.MIMIC_ENTITY, WishCapability.POWERFUL_ENEMY,
                    WishCapability.ENTITY_RECREATION).contains(provided);
            case DESPAWN_ENTITY, ENTITY_SUPPRESSION -> provided == WishCapability.ENTITY_REMOVAL;
            case RESTORE_ENTITY_SPAWNING -> provided == WishCapability.WORLD_EVENT
                    || provided == WishCapability.SPAWN_ENTITY
                    || provided == WishCapability.ENTITY_RECREATION;
            case APPLY_EFFECT, APPLY_EFFECT_CATEGORY -> Set.of(WishCapability.POWER_BUFF, WishCapability.POWER_DEBUFF,
                    WishCapability.HEALING, WishCapability.DAMAGE, WishCapability.DARKNESS,
                    WishCapability.IMMORTALITY).contains(provided);
            case REMOVE_EFFECT, CLEAR_EFFECTS -> Set.of(WishCapability.POWER_BUFF, WishCapability.POWER_DEBUFF,
                    WishCapability.DARKNESS).contains(provided);
            case TELEPORT -> Set.of(WishCapability.TELEPORT, WishCapability.DIMENSION_TRAVEL,
                    WishCapability.SPACE_TRAVEL, WishCapability.SPACECRAFT).contains(provided);
            case CHANGE_TIME -> provided == WishCapability.CHANGE_TIME || provided == WishCapability.WORLD_EVENT;
            case CHANGE_WEATHER -> provided == WishCapability.CHANGE_WEATHER || provided == WishCapability.WORLD_EVENT;
            case PLAY_SOUND -> provided == WishCapability.SOUND_EVENT;
            case SPAWN_PARTICLE -> provided == WishCapability.VISUAL_EVENT || provided == WishCapability.HALLUCINATION;
            case LIGHTNING -> provided == WishCapability.LIGHTNING || provided == WishCapability.WORLD_EVENT;
            case EXPLOSION -> provided == WishCapability.EXPLOSION;
            case CHANGE_BLOCK, REPLACE_BLOCK_AREA, PLACE_BLOCK_PATTERN -> provided == WishCapability.BLOCK_CHANGE
                    || provided == WishCapability.STRUCTURE;
            case FALLING_BLOCK_SHOWER -> provided == WishCapability.GIVE_ITEM
                    || provided == WishCapability.INVENTORY_CHANGE
                    || provided == WishCapability.BLOCK_CHANGE
                    || provided == WishCapability.WORLD_EVENT;
            case CREATE_STRUCTURE -> provided == WishCapability.STRUCTURE;
            case MODIFY_HEALTH -> Set.of(WishCapability.HEALING, WishCapability.DAMAGE,
                    WishCapability.IMMORTALITY, WishCapability.POWER_BUFF,
                    WishCapability.POWER_DEBUFF).contains(provided);
            case MODIFY_HUNGER, MODIFY_ATTRIBUTE -> Set.of(WishCapability.PLAYER_ATTRIBUTE,
                    WishCapability.POWER_BUFF, WishCapability.POWER_DEBUFF).contains(provided);
            case CHANGE_MOB_TARGET, FOLLOW_PLAYER, AVOID_PLAYER, ENTITY_ATTRACTION_AURA -> Set.of(WishCapability.MOB_BEHAVIOR,
                    WishCapability.STALKING_ENTITY, WishCapability.PERSISTENT_FOLLOWER,
                    WishCapability.FRIENDLY_ENTITY, WishCapability.HOSTILE_ENTITY).contains(provided);
            case CHANGE_REPUTATION -> provided == WishCapability.REPUTATION;
            case START_PREDEFINED_EVENT -> provided == WishCapability.WORLD_EVENT
                    || provided == WishCapability.MEMORY_RELATED_EVENT
                    || provided == WishCapability.POWER_BUFF;
        };
    }

    private static void validateReference(CandidateReference candidate, WishActionType action,
                                          JsonObject parameters) {
        if (candidate == null) reject(WishExecutionAcceptError.INVALID_CANDIDATE, "Missing candidate reference");
        RegistryEntryType expected = expectedResource(action);
        var resource = candidate.registryResource();
        if (expected != null && (resource == null || resource.type() != expected)) {
            reject(WishExecutionAcceptError.INVALID_RESOURCE, "Action requires registry type " + expected);
        }
        if (action == WishActionType.TELEPORT) {
            String mode = string(parameters, "mode");
            if ("CANDIDATE_DIMENSION".equals(mode)) {
                if (resource == null || resource.type() != RegistryEntryType.DIMENSION) {
                    reject(WishExecutionAcceptError.INVALID_RESOURCE, "Dimension teleport requires a dimension resource");
                }
            } else if (resource != null || !isTrustedBuiltin(candidate)) {
                reject(WishExecutionAcceptError.UNTRUSTED_REGISTRY_CANDIDATE,
                        "Nearby teleport must use the vanilla builtin candidate");
            }
        } else if (action == WishActionType.START_PREDEFINED_EVENT) {
            if (resource != null || candidate.sourceKind() != CandidateSourceKind.MOD_FEATURE
                    || !WishingWillow.MOD_ID.equals(candidate.sourceModId())
                    || !PredefinedWishEventRegistry.contains(candidate.featureName())) {
                reject(WishExecutionAcceptError.INVALID_EVENT, "Event is not server-whitelisted");
            }
        } else if (expected == null && (resource != null || !isTrustedBuiltin(candidate))) {
            reject(WishExecutionAcceptError.UNTRUSTED_REGISTRY_CANDIDATE,
                    "Action must use a vanilla builtin candidate");
        }
        if (!supports(candidate.requestedCapability(), candidate.providedCapability(), action)) {
            reject(WishExecutionAcceptError.INVALID_ACTION_CAPABILITY,
                    "Candidate capability cannot execute action " + action);
        }
        if (candidate.sourceKind() == CandidateSourceKind.WISHING_WILLOW_BUILTIN
                && !WishingWillowBuiltinCapability.supports(candidate, action)) {
            reject(WishExecutionAcceptError.INVALID_ACTION_CAPABILITY,
                    "Wishing Willow built-in cannot execute action " + action);
        }
    }

    public static boolean isTrustedBuiltin(CandidateReference candidate) {
        if (WishingWillowBuiltinCapability.isTrusted(candidate)) return true;
        return candidate != null && candidate.registryResource() == null
                && candidate.sourceKind() == CandidateSourceKind.VANILLA_BUILTIN
                && "minecraft".equals(candidate.sourceModId())
                && candidate.providedCapability().name().equals(candidate.featureName());
    }

    private static void validateTiming(WishStepTiming timing, int delay, WishTriggerType trigger) {
        boolean valid = switch (timing) {
            case IMMEDIATE -> delay == 0 && trigger == WishTriggerType.NONE;
            case DELAYED -> delay >= 1 && delay <= 86400 && trigger == WishTriggerType.AFTER_DELAY;
            case TRIGGERED -> delay == 0 && trigger != WishTriggerType.NONE && trigger != WishTriggerType.AFTER_DELAY;
            case DELAYED_AFTER_TRIGGER -> delay >= 1 && delay <= 86400
                    && trigger != WishTriggerType.NONE && trigger != WishTriggerType.AFTER_DELAY;
        };
        if (!valid) reject(WishExecutionAcceptError.INVALID_PARAMETER, "Invalid timing/trigger combination");
    }

    private static void validateParameters(WishActionType action, JsonObject p, WishTargetType target,
                                           int severity, CandidateReference candidate) {
        switch (action) {
            case GIVE_ITEM, REMOVE_ITEM -> { keys(p, Set.of("count")); rangeInt(p, "count", 1, 64); }
            case SPAWN_ENTITY -> { keys(p, Set.of("count", "distance_min", "distance_max")); rangeInt(p, "count", 1, 10); distance(p, 128); }
            case DESPAWN_ENTITY -> { keys(p, Set.of("radius", "max_count")); rangeInt(p, "radius", 2, 64); rangeInt(p, "max_count", 1, 32); }
            case APPLY_EFFECT -> { keys(p, Set.of("duration_seconds", "amplifier")); rangeInt(p, "duration_seconds", 1, 3600); rangeInt(p, "amplifier", 0, 4); }
            case REMOVE_EFFECT -> keys(p, Set.of());
            case CLEAR_EFFECTS -> keys(p, Set.of());
            case APPLY_EFFECT_CATEGORY -> {
                keys(p, Set.of("category", "duration_seconds", "amplifier"));
                oneOf(p, "category", Set.of("BENEFICIAL", "HARMFUL", "NEUTRAL"));
                rangeInt(p, "duration_seconds", 1, 3600); rangeInt(p, "amplifier", 0, 4);
            }
            case TELEPORT -> {
                String mode = oneOf(p, "mode", Set.of("NEARBY_SAFE", "RANDOM_SAFE", "CANDIDATE_DIMENSION"));
                if ("CANDIDATE_DIMENSION".equals(mode)) keys(p, Set.of("mode"));
                else { keys(p, Set.of("mode", "distance_min", "distance_max")); distance(p, 4096); }
            }
            case CHANGE_TIME -> { keys(p, Set.of("value")); oneOf(p, "value", Set.of("DAY", "NIGHT", "DAWN", "DUSK")); }
            case CHANGE_WEATHER -> { keys(p, Set.of("weather", "duration_seconds")); oneOf(p, "weather", Set.of("CLEAR", "RAIN", "THUNDER")); rangeInt(p, "duration_seconds", 30, 3600); }
            case PLAY_SOUND -> { keys(p, Set.of("volume", "pitch", "distance")); range(p, "volume", .1, 4); range(p, "pitch", .5, 2); rangeInt(p, "distance", 2, 128); }
            case SPAWN_PARTICLE -> { keys(p, Set.of("count", "radius")); rangeInt(p, "count", 1, 512); range(p, "radius", 0, 32); }
            case LIGHTNING -> { keys(p, Set.of("count", "distance_min", "distance_max")); rangeInt(p, "count", 1, 4); distance(p, 64); }
            case EXPLOSION -> {
                keys(p, Set.of("power", "destroy_blocks", "distance_min", "distance_max"));
                range(p, "power", .1, 8); bool(p, "destroy_blocks"); distance(p, 128);
                if (severity < 41 || bool(p, "destroy_blocks") && severity < 61
                        || number(p, "power") > 4 && severity < 81) {
                    reject(WishExecutionAcceptError.BUDGET_EXCEEDED, "Explosion exceeds severity budget");
                }
            }
            case CHANGE_BLOCK -> { keys(p, Set.of("distance_min", "distance_max")); distance(p, 64); }
            case REPLACE_BLOCK_AREA -> {
                keys(p, Set.of("radius", "max_blocks")); rangeInt(p, "radius", 1, 16);
                rangeInt(p, "max_blocks", 1, 2048);
                if (severity < 41) reject(WishExecutionAcceptError.BUDGET_EXCEEDED, "Block replacement requires severity 41");
            }
            case PLACE_BLOCK_PATTERN -> {
                keys(p, Set.of("pattern", "count"));
                oneOf(p, "pattern", Set.of("ENCLOSURE", "PILLAR", "ROOM"));
                rangeInt(p, "count", 1, 2048);
            }
            case FALLING_BLOCK_SHOWER -> {
                keys(p, Set.of("count", "spawn_height", "radius", "interval_ticks", "landing_mode", "spread"));
                rangeInt(p, "count", 1, com.ikunkk02.wishingwillow.planning.WishPlanBudget.MAX_FALLING_BLOCKS);
                rangeInt(p, "spawn_height", 8, 64);
                rangeInt(p, "radius", 1, 32);
                rangeInt(p, "interval_ticks", 1, 20);
                oneOf(p, "landing_mode", Set.of("PLACE", "DROP_ITEM", "PLACE_OR_DROP", "DELIVER_TO_PLAYER"));
                oneOf(p, "spread", Set.of("RANDOM"));
            }
            case ITEM_RAIN -> {
                keys(p, Set.of("count", "spawn_height", "radius", "interval_ticks", "delivery_mode"));
                rangeInt(p, "count", 1, WishPlanBudget.MAX_ITEM_UNITS);
                rangeInt(p, "spawn_height", 8, 64);
                rangeInt(p, "radius", 1, 32);
                rangeInt(p, "interval_ticks", 1, 20);
                oneOf(p, "delivery_mode", Set.of("WORLD_ITEMS", "DELIVER_TO_PLAYER"));
            }
            case CREATE_STRUCTURE -> {
                keys(p, Set.of("template"));
                oneOf(p, "template", Set.of("SIMPLE_HOUSE"));
            }
            case MODIFY_HEALTH -> {
                keys(p, Set.of("delta", "allow_lethal")); range(p, "delta", -40, 40); bool(p, "allow_lethal");
                if (bool(p, "allow_lethal") && severity < 81) reject(WishExecutionAcceptError.BUDGET_EXCEEDED, "Lethal health change requires severity 81");
            }
            case MODIFY_HUNGER -> { keys(p, Set.of("delta")); rangeInt(p, "delta", -20, 20); }
            case MODIFY_ATTRIBUTE -> {
                keys(p, Set.of("attribute", "operation", "amount", "duration_seconds"));
                oneOf(p, "attribute", Set.of("MAX_HEALTH", "MOVEMENT_SPEED", "ATTACK_DAMAGE", "ARMOR", "KNOCKBACK_RESISTANCE", "LUCK"));
                String operation = oneOf(p, "operation", Set.of("ADD", "MULTIPLY"));
                range(p, "amount", operation.equals("ADD") ? -20 : -1, operation.equals("ADD") ? 20 : 1);
                rangeInt(p, "duration_seconds", 1, 3600);
            }
            case CHANGE_MOB_TARGET -> { keys(p, Set.of("radius", "max_entities", "disposition")); schemaRangeInt(action, p, "radius"); schemaRangeInt(action, p, "max_entities"); oneOf(p, "disposition", Set.of("PLAYER", "NEAREST_HOSTILE", "CLEAR")); }
            case FOLLOW_PLAYER, AVOID_PLAYER -> { keys(p, Set.of("radius", "max_entities", "duration_seconds")); schemaRangeInt(action, p, "radius"); schemaRangeInt(action, p, "max_entities"); schemaRangeInt(action, p, "duration_seconds"); }
            case CHANGE_REPUTATION -> { keys(p, Set.of("delta", "radius")); rangeInt(p, "delta", -100, 100); rangeInt(p, "radius", 2, 64); }
            case START_PREDEFINED_EVENT -> { keys(p, Set.of("intensity")); rangeInt(p, "intensity", 1, 5); }
            case ENTITY_ATTRACTION_AURA -> {
                keys(p, Set.of("radius", "strength", "permanent", "include_hostile", "include_passive", "include_villagers", "include_modded"));
                range(p, "radius", 8, 256);
                range(p, "strength", 0.1, 3);
            }
            case RESTORE_ENTITY_SPAWNING -> {
                keys(p, Set.of("group", "scope", "initial_count", "radius"));
                oneOf(p, "group", Set.of("all_mobs", "hostile", "passive", "neutral", "animals", "monsters", "villagers"));
                oneOf(p, "scope", Set.of("current_dimension", "all_dimensions"));
                rangeInt(p, "initial_count", 1, 32);
                rangeInt(p, "radius", 4, 32);
            }
        }
        if ((action == WishActionType.CHANGE_TIME || action == WishActionType.CHANGE_WEATHER)
                && target != WishTargetType.WORLD) {
            reject(WishExecutionAcceptError.INVALID_PARAMETER, "World action requires WORLD target");
        }
    }

    private static void keys(JsonObject p, Set<String> exact) {
        if (p == null || !p.keySet().equals(exact)) reject(WishExecutionAcceptError.INVALID_PARAMETER, "Unexpected or missing action parameter");
    }
    private static void distance(JsonObject p, int max) { rangeInt(p, "distance_min", 2, max); rangeInt(p, "distance_max", 2, max); if (integer(p, "distance_min") > integer(p, "distance_max")) reject(WishExecutionAcceptError.INVALID_PARAMETER, "Minimum distance exceeds maximum distance"); }
    private static void rangeInt(JsonObject p, String key, int min, int max) { int value = integer(p, key); if (value < min || value > max) reject(WishExecutionAcceptError.INVALID_PARAMETER, key + " is outside allowed range"); }
    private static void schemaRangeInt(WishActionType action, JsonObject p, String key) {
        WishActionDefinition definition = WishActionRegistry.defaults().definition(action);
        JsonObject property = definition == null ? null
                : definition.parameterSchema().getAsJsonObject("properties").getAsJsonObject(key);
        if (property == null || !property.has("minimum") || !property.has("maximum")) {
            reject(WishExecutionAcceptError.INVALID_PARAMETER, "Missing schema range for " + action + "." + key);
        }
        rangeInt(p, key, property.get("minimum").getAsInt(), property.get("maximum").getAsInt());
    }
    private static void range(JsonObject p, String key, double min, double max) { double value = number(p, key); if (!Double.isFinite(value) || value < min || value > max) reject(WishExecutionAcceptError.INVALID_PARAMETER, key + " is outside allowed range"); }
    private static int integer(JsonObject p, String key) { JsonElement e = p.get(key); if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) reject(WishExecutionAcceptError.INVALID_PARAMETER, "Missing integer " + key); String raw=e.getAsString(); if(!raw.matches("-?(0|[1-9][0-9]*)")) reject(WishExecutionAcceptError.INVALID_PARAMETER,"Invalid integer "+key); try{return Integer.parseInt(raw);}catch(NumberFormatException ex){reject(WishExecutionAcceptError.INVALID_PARAMETER,"Invalid integer "+key);return 0;} }
    private static double number(JsonObject p, String key) { JsonElement e = p.get(key); if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) reject(WishExecutionAcceptError.INVALID_PARAMETER, "Missing number " + key); return e.getAsDouble(); }
    private static boolean bool(JsonObject p, String key) { JsonElement e = p.get(key); if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isBoolean()) reject(WishExecutionAcceptError.INVALID_PARAMETER, "Missing boolean " + key); return e.getAsBoolean(); }
    private static String string(JsonObject p, String key) { JsonElement e = p.get(key); if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString()) reject(WishExecutionAcceptError.INVALID_PARAMETER, "Missing string " + key); return e.getAsString(); }
    private static String oneOf(JsonObject p, String key, Set<String> allowed) { String value = string(p, key); if (!allowed.contains(value)) reject(WishExecutionAcceptError.INVALID_PARAMETER, "Invalid " + key); return value; }
    private static void reject(WishExecutionAcceptError error, String detail) { throw new Rejected(error, detail); }

    private static final class Rejected extends RuntimeException {
        private final WishExecutionAcceptError error;
        private Rejected(WishExecutionAcceptError error, String detail) { super(detail); this.error = error; }
    }
}
