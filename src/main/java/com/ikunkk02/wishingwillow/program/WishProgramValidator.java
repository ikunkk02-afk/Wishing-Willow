package com.ikunkk02.wishingwillow.program;

import com.google.gson.JsonObject;
import com.ikunkk02.wishingwillow.WishingWillow;
import com.ikunkk02.wishingwillow.ai.WishCapability;
import com.ikunkk02.wishingwillow.execution.WishExecutionConfig;
import com.ikunkk02.wishingwillow.execution.WishExecutionSafety;
import com.ikunkk02.wishingwillow.execution.action.WishActionDefinition;
import com.ikunkk02.wishingwillow.execution.action.WishActionRegistry;
import com.ikunkk02.wishingwillow.planning.CandidateReference;
import com.ikunkk02.wishingwillow.planning.CandidateSourceKind;
import com.ikunkk02.wishingwillow.planning.MatchType;
import com.ikunkk02.wishingwillow.planning.WishActionType;
import com.ikunkk02.wishingwillow.planning.WishPlanBudget;
import com.ikunkk02.wishingwillow.planning.WishTargetType;
import com.ikunkk02.wishingwillow.program.skill.WishSkillRegistry;
import com.ikunkk02.wishingwillow.research.FeatureType;
import com.ikunkk02.wishingwillow.research.KnowledgeLevel;
import com.ikunkk02.wishingwillow.research.RegistryEntryType;
import com.ikunkk02.wishingwillow.research.VerifiedRegistryResource;
import net.minecraftforge.common.ForgeConfigSpec;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Server-side validation and canonicalization for Wish Programs on the NEW path.
 *
 * <p>The client is untrusted: the server re-runs strict schema validation, live registry
 * resolution, safety policy gates and budget checks. The output is a
 * {@link ValidatedWishProgram} of native {@link ProgramAction} leaves that
 * {@code WishActionManager.startProgram} executes directly.</p>
 *
 * <p>This class intentionally never references the legacy planning stack:
 * {@code DirectActionPlanCompiler}, {@code WishPlanValidator}, {@code WishContractValidator},
 * {@code WishContractReviewer}, {@code WishPlanStore} or {@code WishPlanDraft}.</p>
 */
public final class WishProgramValidator {
    private static final WishActionRegistry ACTIONS = WishActionRegistry.defaults();

    private WishProgramValidator() { }

    public static ValidatedWishProgram validate(WishProgram program,
                                                WishProgramResourceResolver resolver) {
        WishProgramJson.validate(program, ACTIONS);
        WishSkillRegistry.defaults().validateSelection(program);
        if (program.requiresAgent()) throw error(WishProgramError.UNKNOWN_CAPABILITY, "requiresAgent");
        CompiledWishProgram compiled = new WishProgramCompiler().compile(program);
        List<ProgramAction> core = new ArrayList<>();
        List<ProgramAction> presentation = new ArrayList<>();
        for (ProgramAction leaf : compiled.coreActions()) core.add(resolve(leaf, resolver));
        for (ProgramAction leaf : compiled.presentationActions()) presentation.add(resolve(leaf, resolver));
        return new ValidatedWishProgram(program, List.copyOf(core), List.copyOf(presentation));
    }

    private static ProgramAction resolve(ProgramAction leaf, WishProgramResourceResolver resolver) {
        WishActionDefinition definition = ACTIONS.find(leaf.actionId());
        if (definition == null || definition.legacyType() == null) {
            throw error(WishProgramError.INVALID_ACTION, leaf.actionId());
        }
        JsonObject parameters = canonicalize(definition.legacyType(), leaf.parameters());
        String resource = resource(parameters, definition);
        WishTargetType target = target(parameters, definition.legacyType());
        WishCapability capability = definition.capabilities().isEmpty()
                ? WishCapability.WORLD_EVENT : definition.capabilities().iterator().next();
        policy(definition, parameters, resource, resolver);
        CandidateReference candidate = candidate(definition, capability, parameters,
                resource, target, resolver);
        return new ProgramAction(leaf.actionId(), parameters, leaf.presentation(), leaf.group(),
                leaf.delayTicks(), target, capability, candidate, leaf.stepIndex());
    }

    private static CandidateReference candidate(WishActionDefinition definition, WishCapability capability,
                                                JsonObject parameters, String resource,
                                                WishTargetType target,
                                                WishProgramResourceResolver resolver) {
        WishActionType type = definition.legacyType();
        String candidateId = "program-" + type.name().toLowerCase(Locale.ROOT);
        if (type == WishActionType.START_PREDEFINED_EVENT) {
            if (!resolver.containsPredefinedEvent(resource)) {
                throw error(WishProgramError.INVALID_REGISTRY, "event=" + resource);
            }
            return new CandidateReference(candidateId, capability, capability, MatchType.EXACT,
                    CandidateSourceKind.MOD_FEATURE, WishingWillow.MOD_ID, "",
                    resource, FeatureType.WORLD_SYSTEM, null, 100, 20);
        }
        if (type == WishActionType.TELEPORT
                && !"CANDIDATE_DIMENSION".equals(parameters.get("mode").getAsString())) {
            return new CandidateReference(candidateId, capability, capability, MatchType.EXACT,
                    CandidateSourceKind.VANILLA_BUILTIN, "minecraft", "1.20.1",
                    capability.name(), FeatureType.WORLD_SYSTEM, null, 100, 25);
        }
        RegistryEntryType registryType = definition.resourceKind();
        if (registryType == null) {
            return new CandidateReference(candidateId, capability, capability, MatchType.EXACT,
                    CandidateSourceKind.VANILLA_BUILTIN, "minecraft", "1.20.1",
                    capability.name(), FeatureType.WORLD_SYSTEM, null, 100, 25);
        }
        String resolved;
        if (type == WishActionType.TELEPORT
                && "CANDIDATE_DIMENSION".equals(parameters.get("mode").getAsString())) {
            resolved = resolver.resolveDimension(resource);
            if (resolved == null) {
                throw error(WishProgramError.INVALID_REGISTRY, "dimension=" + resource);
            }
            registryType = RegistryEntryType.DIMENSION;
        } else {
            resolved = resolver.resolve(registryType, resource);
            if (resolved == null) {
                RegistryEntryType actual = actualRegistryType(resolver, resource, registryType);
                if (actual != null) {
                    String detail = "action=" + definition.id() + " parameter="
                            + definition.resourceParameter() + " resource=" + resource
                            + " expected=" + registryType + " actual=" + actual;
                    WishingWillow.LOGGER.warn("RESOURCE_KIND_MISMATCH {}", detail);
                    throw error(WishProgramError.RESOURCE_KIND_MISMATCH, detail);
                }
                throw error(WishProgramError.INVALID_REGISTRY,
                        type.name().toLowerCase(Locale.ROOT) + "=" + resource);
            }
        }
        VerifiedRegistryResource verified = new VerifiedRegistryResource(registryType, resolved);
        String namespace = resolved.substring(0, resolved.indexOf(':'));
        CandidateSourceKind kind = namespace.equals("minecraft")
                ? CandidateSourceKind.VANILLA_REGISTRY : CandidateSourceKind.MOD_FEATURE;
        return new CandidateReference(candidateId, capability, capability, MatchType.EXACT,
                kind, namespace, namespace.equals("minecraft") ? "1.20.1" : "",
                resolved, feature(registryType), verified, 100, 20);
    }

    /** Server-side policy gates + hard budget checks (client input is never trusted). */
    private static void policy(WishActionDefinition definition, JsonObject parameters,
                               String resource, WishProgramResourceResolver resolver) {
        WishActionType type = definition.legacyType();
        if (type == WishActionType.GIVE_ITEM || type == WishActionType.REMOVE_ITEM) {
            int count = parameters.get("count").getAsInt();
            if (count < 1 || count > 4096) throw error(WishProgramError.BUDGET_EXCEEDED, "count=" + count);
        }
        if (type == WishActionType.SPAWN_ENTITY) {
            int count = parameters.get("count").getAsInt();
            if (count < 1 || count > 64) throw error(WishProgramError.BUDGET_EXCEEDED, "count=" + count);
            if (resource != null && !resource.startsWith("minecraft:")
                    && !configEnabled(WishExecutionConfig.THIRD_PARTY_ENTITIES)) {
                throw error(WishProgramError.EXECUTION_DISABLED, "third_party_entities");
            }
        }
        if (type == WishActionType.FALLING_BLOCK_SHOWER) {
            int count = parameters.get("count").getAsInt();
            if (count < 1 || count > WishPlanBudget.MAX_FALLING_BLOCKS) {
                throw error(WishProgramError.BUDGET_EXCEEDED, "count=" + count);
            }
        }
        if (type == WishActionType.ITEM_RAIN) {
            int count = parameters.get("count").getAsInt();
            if (count < 1 || count > WishPlanBudget.MAX_ITEM_UNITS) {
                throw error(WishProgramError.BUDGET_EXCEEDED, "count=" + count);
            }
            if (resolver.resolve(RegistryEntryType.ITEM, resource) != null) {
                int stackSize = Math.max(1, resolver.maxStackSize(RegistryEntryType.ITEM, resource));
                int entities = (count + stackSize - 1) / stackSize;
                if (entities > WishPlanBudget.MAX_ACTIVE_ITEM_RAIN_ENTITIES) {
                    throw error(WishProgramError.BUDGET_EXCEEDED, "item_entities=" + entities);
                }
            }
        }
        if (type == WishActionType.EXPLOSION) {
            double power = parameters.get("power").getAsDouble();
            if (!WishExecutionSafety.validExplosionPower(power)) {
                throw error(WishProgramError.INVALID_PARAMETER, "power=" + power);
            }
            if (!configEnabled(WishExecutionConfig.EXPLOSIONS)) {
                throw error(WishProgramError.EXECUTION_DISABLED, "explosions");
            }
        }
        if (type == WishActionType.REPLACE_BLOCK_AREA) {
            int radius = parameters.get("radius").getAsInt();
            int blocks = parameters.get("max_blocks").getAsInt();
            if (!WishExecutionSafety.validBlockLimit(radius, blocks)) {
                throw error(WishProgramError.BUDGET_EXCEEDED, "radius=" + radius + " blocks=" + blocks);
            }
        }
        if (Set.of(WishActionType.CHANGE_BLOCK, WishActionType.REPLACE_BLOCK_AREA,
                WishActionType.PLACE_BLOCK_PATTERN, WishActionType.CREATE_STRUCTURE,
                WishActionType.FALLING_BLOCK_SHOWER).contains(type)
                && !configEnabled(WishExecutionConfig.BLOCK_MODIFICATION)) {
            throw error(WishProgramError.EXECUTION_DISABLED, "block_modification");
        }
        if (type == WishActionType.TELEPORT
                && "CANDIDATE_DIMENSION".equals(parameters.get("mode").getAsString())
                && !configEnabled(WishExecutionConfig.CROSS_DIMENSION_TELEPORT)) {
            throw error(WishProgramError.EXECUTION_DISABLED, "cross_dimension_teleport");
        }
    }

    /** Config gates apply only once the server config is actually loaded (in-game). */
    private static boolean configEnabled(ForgeConfigSpec.BooleanValue value) {
        return !WishExecutionConfig.SPEC.isLoaded() || value.get();
    }

    private static JsonObject canonicalize(WishActionType type, JsonObject raw) {
        JsonObject parameters = raw.deepCopy();
        rename(parameters, "group", "category");
        switch (type) {
            case APPLY_EFFECT -> { defaultInt(parameters, "duration_seconds", 600); defaultInt(parameters, "amplifier", 0); }
            case APPLY_EFFECT_CATEGORY -> { defaultInt(parameters, "duration_seconds", 600); defaultInt(parameters, "amplifier", 0); }
            case SPAWN_ENTITY -> { defaultInt(parameters, "count", 1); defaultInt(parameters, "distance_min", 2); defaultInt(parameters, "distance_max", 8); }
            case DESPAWN_ENTITY -> { defaultInt(parameters, "max_count", 16); defaultInt(parameters, "radius", 16); }
            case CHANGE_WEATHER -> { upper(parameters, "weather"); defaultInt(parameters, "duration_seconds", 300); }
            case CHANGE_TIME -> {
                upper(parameters, "value");
                if (parameters.has("value")) {
                    String value = parameters.get("value").getAsString();
                    parameters.addProperty("value", switch (value) {
                        case "MIDNIGHT" -> "NIGHT";
                        case "NOON" -> "DUSK";
                        default -> value;
                    });
                }
            }
            case PLAY_SOUND -> { defaultNumber(parameters, "volume", 1.0); defaultNumber(parameters, "pitch", 1.0); defaultInt(parameters, "distance", 32); }
            case SPAWN_PARTICLE -> { defaultInt(parameters, "count", 64); defaultNumber(parameters, "radius", 2.0); }
            case LIGHTNING -> { defaultInt(parameters, "count", 1); defaultInt(parameters, "distance_min", 2); defaultInt(parameters, "distance_max", 8); }
            case EXPLOSION -> { defaultNumber(parameters, "power", 2.0); defaultBoolean(parameters, "destroy_blocks", false); defaultInt(parameters, "distance_min", 8); defaultInt(parameters, "distance_max", 16); }
            case CHANGE_BLOCK -> { defaultInt(parameters, "distance_min", 2); defaultInt(parameters, "distance_max", 8); }
            case REPLACE_BLOCK_AREA -> { defaultInt(parameters, "radius", 3); defaultInt(parameters, "max_blocks", 64); }
            case PLACE_BLOCK_PATTERN -> { upper(parameters, "pattern"); defaultString(parameters, "pattern", "ENCLOSURE"); defaultInt(parameters, "count", 16); }
            case CREATE_STRUCTURE -> { upper(parameters, "structure"); defaultString(parameters, "structure", "SIMPLE_HOUSE"); }
            case MODIFY_HEALTH -> { defaultNumber(parameters, "delta", 0.0); defaultBoolean(parameters, "allow_lethal", false); }
            case MODIFY_HUNGER -> defaultInt(parameters, "delta", 0);
            case MODIFY_ATTRIBUTE -> { upper(parameters, "attribute"); upper(parameters, "operation"); defaultString(parameters, "operation", "ADD"); defaultNumber(parameters, "amount", 1.0); defaultInt(parameters, "duration_seconds", 600); }
            case CHANGE_MOB_TARGET -> { upper(parameters, "disposition"); defaultString(parameters, "disposition", "PLAYER"); defaultInt(parameters, "max_entities", 8); defaultInt(parameters, "radius", 16); }
            case FOLLOW_PLAYER, AVOID_PLAYER -> { defaultInt(parameters, "max_entities", 8); defaultInt(parameters, "radius", 16); defaultInt(parameters, "duration_seconds", 600); }
            case CHANGE_REPUTATION -> { defaultInt(parameters, "delta", 10); defaultInt(parameters, "radius", 16); }
            case START_PREDEFINED_EVENT -> defaultInt(parameters, "intensity", 3);
            default -> { }
        }
        if (type == WishActionType.FALLING_BLOCK_SHOWER) {
            rename(parameters, "height", "spawn_height");
            rename(parameters, "horizontal_radius", "radius");
            rename(parameters, "landing", "landing_mode");
            defaultInt(parameters, "count", 1);
            defaultInt(parameters, "spawn_height", 28);
            defaultInt(parameters, "radius", 10);
            defaultInt(parameters, "interval_ticks", 2);
            clampInt(parameters, "spawn_height", 8, 64);
            clampInt(parameters, "radius", 1, 32);
            clampInt(parameters, "interval_ticks", 1, 20);
            if (!parameters.has("spread")) parameters.addProperty("spread", "RANDOM");
            if (parameters.has("landing_mode")) {
                String landing = parameters.get("landing_mode").getAsString().toUpperCase(Locale.ROOT);
                parameters.addProperty("landing_mode", switch (landing) {
                    case "PLACE_OR_DROP" -> "PLACE_OR_DROP";
                    case "DROP", "DROP_ITEM" -> "DROP_ITEM";
                    case "PLACE" -> "PLACE";
                    default -> "DELIVER_TO_PLAYER";
                });
            } else {
                String target = parameters.has("target")
                        ? parameters.get("target").getAsString().toUpperCase(Locale.ROOT) : "SELF";
                parameters.addProperty("landing_mode",
                        "SELF".equals(target) ? "DELIVER_TO_PLAYER" : "PLACE_OR_DROP");
            }
        }
        if (type == WishActionType.ITEM_RAIN) {
            rename(parameters, "height", "spawn_height");
            rename(parameters, "horizontal_radius", "radius");
            rename(parameters, "delivery", "delivery_mode");
            defaultInt(parameters, "spawn_height", 24);
            defaultInt(parameters, "radius", 8);
            defaultInt(parameters, "interval_ticks", 2);
            defaultString(parameters, "delivery_mode", "WORLD_ITEMS");
            upper(parameters, "delivery_mode");
            clampInt(parameters, "spawn_height", 8, 64);
            clampInt(parameters, "radius", 1, 32);
            clampInt(parameters, "interval_ticks", 1, 20);
        }
        if (type == WishActionType.TELEPORT && parameters.has("dimension")) {
            parameters.addProperty("mode", "CANDIDATE_DIMENSION");
        } else if (type == WishActionType.TELEPORT) {
            upper(parameters, "mode"); defaultString(parameters, "mode", "NEARBY_SAFE");
            defaultInt(parameters, "distance_min", 2); defaultInt(parameters, "distance_max", 32);
        }
        if (type == WishActionType.APPLY_EFFECT_CATEGORY && parameters.has("category")) {
            parameters.addProperty("category", parameters.get("category").getAsString().toUpperCase(Locale.ROOT));
        }
        return parameters;
    }

    private static String resource(JsonObject parameters, WishActionDefinition definition) {
        String key = definition.legacyType() == WishActionType.START_PREDEFINED_EVENT
                ? "event" : definition.resourceParameter();
        return key.isEmpty() || !parameters.has(key) ? "" : parameters.get(key).getAsString();
    }

    private static WishTargetType target(JsonObject parameters, WishActionType type) {
        if (parameters.has("target")) {
            String value = parameters.get("target").getAsString().toUpperCase(Locale.ROOT);
            if (Set.of("WORLD", "AREA", "NEARBY_ENTITIES").contains(value)) {
                return WishTargetType.valueOf(value);
            }
        }
        return switch (type) {
            case CHANGE_TIME, CHANGE_WEATHER -> WishTargetType.WORLD;
            case CHANGE_BLOCK, REPLACE_BLOCK_AREA, PLACE_BLOCK_PATTERN -> WishTargetType.AREA;
            case DESPAWN_ENTITY, CHANGE_MOB_TARGET, FOLLOW_PLAYER, AVOID_PLAYER -> WishTargetType.NEARBY_ENTITIES;
            default -> WishTargetType.PLAYER;
        };
    }

    @Nullable
    private static RegistryEntryType actualRegistryType(WishProgramResourceResolver resolver,
                                                        String resource,
                                                        RegistryEntryType expected) {
        for (RegistryEntryType type : List.of(RegistryEntryType.ITEM, RegistryEntryType.BLOCK,
                RegistryEntryType.ENTITY, RegistryEntryType.EFFECT, RegistryEntryType.SOUND,
                RegistryEntryType.PARTICLE)) {
            if (type != expected && resolver.resolve(type, resource) != null) return type;
        }
        return null;
    }

    private static FeatureType feature(RegistryEntryType type) {
        if (type == null) return FeatureType.WORLD_SYSTEM;
        return switch (type) {
            case ITEM -> FeatureType.ITEM;
            case BLOCK -> FeatureType.BLOCK;
            case ENTITY -> FeatureType.ENTITY;
            case EFFECT -> FeatureType.EFFECT;
            case SOUND -> FeatureType.SOUND;
            case DIMENSION -> FeatureType.DIMENSION;
            default -> FeatureType.UNKNOWN;
        };
    }

    private static void rename(JsonObject object, String from, String to) {
        if (object.has(from)) object.add(to, object.remove(from));
    }

    private static void upper(JsonObject object, String key) {
        if (object.has(key)) object.addProperty(key, object.get(key).getAsString().toUpperCase(Locale.ROOT));
    }
    private static void defaultInt(JsonObject object, String key, int value) { if (!object.has(key)) object.addProperty(key, value); }
    private static void defaultNumber(JsonObject object, String key, double value) { if (!object.has(key)) object.addProperty(key, value); }
    private static void defaultBoolean(JsonObject object, String key, boolean value) { if (!object.has(key)) object.addProperty(key, value); }
    private static void defaultString(JsonObject object, String key, String value) { if (!object.has(key)) object.addProperty(key, value); }
    private static void clampInt(JsonObject object, String key, int min, int max) {
        if (object.has(key) && object.get(key).isJsonPrimitive() && object.get(key).getAsJsonPrimitive().isNumber()) {
            object.addProperty(key, Math.max(min, Math.min(max, object.get(key).getAsInt())));
        }
    }

    private static IllegalArgumentException error(WishProgramError error, String detail) {
        return new IllegalArgumentException(error.name() + ":" + detail);
    }
}
