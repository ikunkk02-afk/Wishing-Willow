package com.ikunkk02.wishingwillow.planning.direct;

import com.google.gson.JsonObject;
import com.ikunkk02.wishingwillow.WishingWillow;
import com.ikunkk02.wishingwillow.ai.WishCapability;
import com.ikunkk02.wishingwillow.ai.WishDelivery;
import com.ikunkk02.wishingwillow.ai.WishFulfillmentMode;
import com.ikunkk02.wishingwillow.ai.WishInterpretation;
import com.ikunkk02.wishingwillow.contract.WishContractValidationState;
import com.ikunkk02.wishingwillow.contract.WishContractValidator;
import com.ikunkk02.wishingwillow.contract.WishConstraintKind;
import com.ikunkk02.wishingwillow.contract.WishContractType;
import com.ikunkk02.wishingwillow.execution.ExecutionSettingsSnapshot;
import com.ikunkk02.wishingwillow.execution.PredefinedWishEventRegistry;
import com.ikunkk02.wishingwillow.execution.WishActionPolicy;
import com.ikunkk02.wishingwillow.planning.*;
import com.ikunkk02.wishingwillow.planning.semantic.WishSemanticRecipeRegistry;
import com.ikunkk02.wishingwillow.research.FeatureType;
import com.ikunkk02.wishingwillow.research.KnowledgeLevel;
import com.ikunkk02.wishingwillow.research.RegistryEntryType;
import com.ikunkk02.wishingwillow.research.VerifiedRegistryResource;
import com.ikunkk02.wishingwillow.research.registry.RegistrySnapshot;
import net.minecraft.resources.ResourceLocation;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Compiles the external Action DSL into the existing authoritative WishPlan draft model. */
public final class DirectActionPlanCompiler {
    private final WishAbsurdityPlanner absurdityPlanner;

    public DirectActionPlanCompiler() {
        this(new WishAbsurdityPlanner());
    }

    DirectActionPlanCompiler(WishAbsurdityPlanner absurdityPlanner) {
        this.absurdityPlanner = absurdityPlanner;
    }

    public CompiledDirectActionPlan compile(DirectActionPlan direct, WishInterpretation interpretation,
                                            CapabilityCatalog initialCatalog, RegistrySnapshot registry,
                                            ExecutionSettingsSnapshot settings) {
        if (direct.route() != WishExecutionRoute.DIRECT_ACTION) throw invalid(WishPlanError.UNSUPPORTED_ACTION);
        if (interpretation.delivery() == WishDelivery.CONDITIONAL
                || interpretation.delivery() == WishDelivery.PROGRESSIVE) {
            throw invalid(WishPlanError.UNSUPPORTED_ACTION);
        }
        CandidateBuilder candidates = new CandidateBuilder(initialCatalog, registry, interpretation);
        List<WishPlanStep> coreSteps = new ArrayList<>();
        for (DirectWishAction action : direct.actions()) {
            if (action.type() == WishActionType.FALLING_BLOCK_SHOWER) {
                WishingWillow.LOGGER.info("Semantic recipe selected recipe=FALLING_BLOCK_SHOWER");
            }
            coreSteps.addAll(compileAction(action, coreSteps.size(), candidates, interpretation, true));
        }
        WishPlanDraft coreDraft = draft(direct.summary(), interpretation, coreSteps);
        PlanningEnvironment environment = new RegistrySnapshotEnvironment(registry);
        CapabilityCatalog coreCatalog = candidates.catalog();
        WishPlanValidation coreValidation = WishPlanValidator.parseAndValidate(WishPlanJson.toAiJson(coreDraft),
                interpretation, coreCatalog, environment, settings);
        var contract = WishContractValidator.validate(interpretation, coreValidation.draft(), environment);
        if (contract.state() == WishContractValidationState.AI_REVIEW_REQUIRED) {
            throw invalid(WishPlanError.UNSUPPORTED_ACTION);
        }
        if (contract.state() != WishContractValidationState.CONTRACT_FULFILLED) {
            throw invalid(WishPlanError.CONTRACT_NOT_FULFILLED);
        }

        List<WishPlanStep> accepted = new ArrayList<>(coreValidation.draft().steps());
        List<DirectWishAction> acceptedModifiers = new ArrayList<>();
        int dropped = 0;
        Set<String> seen = new LinkedHashSet<>();
        WishAbsurdityProfile requestedAbsurdity = normalizeAbsurdity(direct.absurdity(), interpretation);
        for (DirectWishAction modifier : absurdityPlanner.candidates(requestedAbsurdity, registry)) {
            if (acceptedModifiers.size() >= 3) break;
            String signature = modifier.type() + "|" + modifier.target() + "|" + modifier.resource()
                    + "|" + modifier.parameters();
            if (!seen.add(signature)) continue;
            try {
                List<WishPlanStep> addition = compileAction(modifier, accepted.size(), candidates,
                        interpretation, false);
                List<WishPlanStep> candidateSteps = new ArrayList<>(accepted); candidateSteps.addAll(addition);
                WishPlanValidation validated = WishPlanValidator.parseAndValidate(
                        WishPlanJson.toAiJson(draft(direct.summary(), interpretation, candidateSteps)),
                        interpretation, candidates.catalog(), environment, settings);
                accepted = new ArrayList<>(validated.draft().steps());
                acceptedModifiers.add(modifier);
            } catch (IllegalArgumentException rejectedModifier) {
                dropped++;
            }
        }

        WishAbsurdityProfile acceptedProfile = new WishAbsurdityProfile(requestedAbsurdity.style(),
                requestedAbsurdity.intensity(), acceptedModifiers);
        WishPlanDraft finalDraft = draft(direct.summary(), interpretation, accepted);
        CapabilityCatalog finalCatalog = candidates.catalog();
        WishPlanValidation finalValidation = WishPlanValidator.parseAndValidate(WishPlanJson.toAiJson(finalDraft),
                interpretation, finalCatalog, environment, settings);
        List<String> actions = new ArrayList<>();
        direct.actions().forEach(action -> actions.add("CORE:" + action.type().name()));
        acceptedModifiers.forEach(action -> actions.add("ABSURD:" + action.type().name()));
        return new CompiledDirectActionPlan(finalValidation.draft(), finalCatalog, acceptedProfile,
                actions, dropped);
    }

    private static List<WishPlanStep> compileAction(DirectWishAction direct, int firstIndex,
                                                    CandidateBuilder candidates,
                                                    WishInterpretation interpretation, boolean core) {
        if (!DirectActionJson.DIRECT_ACTIONS.contains(direct.type())) {
            throw invalid(WishPlanError.UNSUPPORTED_ACTION);
        }
        RegistryEntryType resourceType = resourceType(direct);
        String resource = direct.resource();
        if (resourceType != null) {
            if (!validResourceId(resource) || !candidates.registry.contains(resourceType, resource)) {
                throw invalid(WishPlanError.INVALID_REGISTRY);
            }
        } else if (direct.type() == WishActionType.START_PREDEFINED_EVENT) {
            if (!PredefinedWishEventRegistry.contains(resource)) throw invalid(WishPlanError.INVALID_EVENT);
        } else if (!resource.isBlank()) {
            throw invalid(WishPlanError.INVALID_PARAMETER);
        }
        WishCapability capability = inferCapability(interpretation, direct.type());
        CapabilityCandidate candidate = candidates.candidate(capability, direct.type(), resourceType, resource);
        JsonObject parameters = direct.parameters().deepCopy();
        WishTargetType target = target(direct.target());
        Timing timing = timing(interpretation.delivery());
        String reason = core ? "Direct Action core fulfillment." : "Validated optional absurd presentation.";

        if (direct.type() == WishActionType.GIVE_ITEM || direct.type() == WishActionType.REMOVE_ITEM) {
            int count = exactInteger(parameters, "count");
            if (count < 1 || count > 4096 || !parameters.keySet().equals(Set.of("count"))) {
                throw invalid(WishPlanError.INVALID_PARAMETER);
            }
            String batch = "direct-items-" + firstIndex;
            List<WishPlanStep> result = new ArrayList<>();
            int remaining = count;
            while (remaining > 0) {
                JsonObject split = new JsonObject(); split.addProperty("count", Math.min(64, remaining));
                result.add(step(firstIndex + result.size(), timing, direct.type(), capability, candidate,
                        target, split, reason, batch));
                remaining -= Math.min(64, remaining);
            }
            return result;
        }
        if (direct.type() == WishActionType.FALLING_BLOCK_SHOWER) {
            if (direct.target() != DirectWishTarget.SELF && direct.target() != DirectWishTarget.AREA) {
                throw invalid(WishPlanError.INVALID_PARAMETER,
                        "falling_block_shower.target_must_be_self_or_area");
            }
            Set<String> allowed = Set.of("count", "spawn_height", "radius", "interval_ticks",
                    "landing_mode", "spread");
            if (!allowed.containsAll(parameters.keySet())) {
                throw invalid(WishPlanError.INVALID_PARAMETER,
                        "falling_block_shower.unexpected_parameters="
                                + parameters.keySet().stream().filter(key -> !allowed.contains(key)).toList());
            }
            int count = integralNumber(parameters, "count", -1);
            int minimum = interpretation.contract().quantity(WishConstraintKind.MINIMUM_QUANTITY).orElse(1);
            count = Math.max(count, minimum);
            if (count < 1) {
                throw invalid(WishPlanError.INVALID_PARAMETER,
                        "falling_block_shower.count_must_be_a_positive_integer");
            }
            if (count > WishPlanBudget.MAX_FALLING_BLOCKS) {
                throw invalid(WishPlanError.BUDGET_EXCEEDED,
                        "falling_block_shower.count_exceeds_" + WishPlanBudget.MAX_FALLING_BLOCKS);
            }
            int height = clamp(integralNumber(parameters, "spawn_height", 28), 8, 64);
            int radius = clamp(integralNumber(parameters, "radius", 10), 1, 32);
            int interval = clamp(integralNumber(parameters, "interval_ticks", 2), 1, 20);
            String defaultLanding = interpretation.contract().type() == WishContractType.OBTAIN_RESOURCE
                    || interpretation.contract().requires(WishConstraintKind.PLAYER_ACCESSIBLE)
                    ? "DELIVER_TO_PLAYER" : "PLACE_OR_DROP";
            String landing = enumParameter(parameters, "landing_mode", defaultLanding,
                    Set.of("PLACE", "DROP_ITEM", "PLACE_OR_DROP", "DELIVER_TO_PLAYER"));
            String spread = enumParameter(parameters, "spread", "RANDOM", Set.of("RANDOM"));
            parameters = fallingBlockParameters(count, height, radius, interval, landing, spread);
            if (WishSemanticRecipeRegistry.resolve(interpretation).isPresent()
                    && !WishSemanticRecipeRegistry.proves(interpretation, direct.type())) {
                throw invalid(WishPlanError.UNSUPPORTED_ACTION);
            }
            WishingWillow.LOGGER.info("Direct action compiled type=FALLING_BLOCK_SHOWER resource={} count={}",
                    resource, count);
        }
        return List.of(step(firstIndex, timing, direct.type(), capability, candidate,
                target, parameters, reason, ""));
    }

    private static WishPlanStep step(int index, Timing timing, WishActionType action,
                                     WishCapability capability, CapabilityCandidate candidate,
                                     WishTargetType target, JsonObject parameters, String reason, String batch) {
        return new WishPlanStep(index, timing.timing, timing.delay, timing.trigger, action, capability,
                candidate.candidateId(), target, parameters, reason, candidate.reference(), batch);
    }

    private static WishPlanDraft draft(String summary, WishInterpretation interpretation,
                                       List<WishPlanStep> steps) {
        return new WishPlanDraft(2, summary, interpretation.delivery(), interpretation.severity(),
                interpretation.delivery() == WishDelivery.DELAYED
                        ? WishEstimatedDuration.SHORT : WishEstimatedDuration.INSTANT, steps);
    }

    private static RegistryEntryType resourceType(DirectWishAction action) {
        return switch (action.type()) {
            case GIVE_ITEM, REMOVE_ITEM -> RegistryEntryType.ITEM;
            case APPLY_EFFECT, REMOVE_EFFECT -> RegistryEntryType.EFFECT;
            case SPAWN_ENTITY, DESPAWN_ENTITY -> RegistryEntryType.ENTITY;
            case PLACE_BLOCK_PATTERN, FALLING_BLOCK_SHOWER, REPLACE_BLOCK_AREA -> RegistryEntryType.BLOCK;
            case PLAY_SOUND -> RegistryEntryType.SOUND;
            case SPAWN_PARTICLE -> RegistryEntryType.PARTICLE;
            case TELEPORT -> "CANDIDATE_DIMENSION".equals(string(action.parameters(), "mode"))
                    ? RegistryEntryType.DIMENSION : null;
            default -> null;
        };
    }

    private static WishCapability inferCapability(WishInterpretation interpretation, WishActionType action) {
        for (WishCapability capability : WishContractCapabilityDeriver.planningCapabilities(interpretation)) {
            if (WishActionPolicy.supports(capability, capability, action)) return capability;
        }
        throw invalid(WishPlanError.UNSUPPORTED_ACTION);
    }

    private static WishTargetType target(DirectWishTarget target) {
        return switch (target) {
            case SELF -> WishTargetType.PLAYER;
            case WORLD -> WishTargetType.WORLD;
            case AREA -> WishTargetType.AREA;
            case NEARBY_ENTITIES -> WishTargetType.NEARBY_ENTITIES;
        };
    }

    private static WishAbsurdityProfile normalizeAbsurdity(WishAbsurdityProfile requested,
                                                            WishInterpretation interpretation) {
        if (interpretation.fulfillment().mode() == WishFulfillmentMode.CLASSIC
                && requested.style() == WishAbsurdityStyle.NONE) return requested;
        int intensity = Math.min(100, Math.max(requested.intensity(),
                Math.max(75, interpretation.fulfillment().absurdity())));
        WishAbsurdityStyle style = requested.style();
        if (style == WishAbsurdityStyle.NONE) {
            style = interpretation.fulfillment().mode() == WishFulfillmentMode.DEVIL
                    ? WishAbsurdityStyle.OMINOUS : WishAbsurdityStyle.CINEMATIC;
        }
        return new WishAbsurdityProfile(style, intensity, requested.modifiers());
    }

    private static Timing timing(WishDelivery delivery) {
        return delivery == WishDelivery.DELAYED
                ? new Timing(WishStepTiming.DELAYED, 1, WishTriggerType.AFTER_DELAY)
                : new Timing(WishStepTiming.IMMEDIATE, 0, WishTriggerType.NONE);
    }

    private static boolean validResourceId(String value) {
        return !value.isBlank() && ResourceLocation.tryParse(value) != null;
    }

    private static int exactInteger(JsonObject object, String name) {
        try {
            if (!object.has(name) || !object.get(name).isJsonPrimitive()
                    || !object.get(name).getAsJsonPrimitive().isNumber()
                    || !object.get(name).getAsString().matches("-?(0|[1-9][0-9]*)")) {
                throw invalid(WishPlanError.INVALID_PARAMETER);
            }
            return object.get(name).getAsInt();
        } catch (RuntimeException error) {
            if (error instanceof IllegalArgumentException illegal) throw illegal;
            throw invalid(WishPlanError.INVALID_PARAMETER);
        }
    }

    /** Canonicalizes provider numbers such as JSON {@code 10.0} when they are mathematically integral. */
    private static int integralNumber(JsonObject object, String name, int defaultValue) {
        if (!object.has(name)) return defaultValue;
        try {
            if (!object.get(name).isJsonPrimitive() || !object.get(name).getAsJsonPrimitive().isNumber()) {
                throw invalid(WishPlanError.INVALID_PARAMETER,
                        "falling_block_shower." + name + "_must_be_an_integer");
            }
            return new BigDecimal(object.get(name).getAsString()).intValueExact();
        } catch (ArithmeticException | NumberFormatException error) {
            throw invalid(WishPlanError.INVALID_PARAMETER,
                    "falling_block_shower." + name + "_must_be_an_integer");
        }
    }

    private static String enumParameter(JsonObject object, String name, String defaultValue,
                                        Set<String> allowed) {
        if (!object.has(name)) return defaultValue;
        String value = string(object, name).strip().toUpperCase(java.util.Locale.ROOT);
        return allowed.contains(value) ? value : defaultValue;
    }

    private static JsonObject fallingBlockParameters(int count, int height, int radius, int interval,
                                                      String landing, String spread) {
        JsonObject canonical = new JsonObject();
        canonical.addProperty("count", count);
        canonical.addProperty("spawn_height", height);
        canonical.addProperty("radius", radius);
        canonical.addProperty("interval_ticks", interval);
        canonical.addProperty("landing_mode", landing);
        canonical.addProperty("spread", spread);
        return canonical;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String string(JsonObject object, String name) {
        try { return object.has(name) ? object.get(name).getAsString() : ""; }
        catch (RuntimeException ignored) { return ""; }
    }

    private static IllegalArgumentException invalid(WishPlanError error) {
        return new IllegalArgumentException(error.name());
    }

    private static IllegalArgumentException invalid(WishPlanError error, String detail) {
        return new IllegalArgumentException(error.name() + "|" + detail);
    }

    private record Timing(WishStepTiming timing, int delay, WishTriggerType trigger) {}

    private static final class CandidateBuilder {
        private final List<CapabilityCandidate> values = new ArrayList<>();
        private final CapabilityCatalog initial;
        private final RegistrySnapshot registry;
        private final WishInterpretation interpretation;

        private CandidateBuilder(CapabilityCatalog initial, RegistrySnapshot registry,
                                 WishInterpretation interpretation) {
            this.initial = initial == null
                    ? CapabilityCatalog.create(List.of(), List.of(), "READY", "", registry.digest()) : initial;
            this.registry = registry;
            this.interpretation = interpretation;
            this.values.addAll(this.initial.candidates());
        }

        private CapabilityCandidate candidate(WishCapability capability, WishActionType action,
                                              RegistryEntryType type, String resource) {
            String feature = action == WishActionType.START_PREDEFINED_EVENT ? resource : capability.name();
            for (CapabilityCandidate existing : values) {
                boolean sameResource = type == null
                        ? existing.registryResource() == null
                        : existing.registryResource() != null && existing.registryResource().type() == type
                        && existing.registryResource().id().equals(resource);
                if (existing.requestedCapability() == capability
                        && existing.providedCapability() == capability && sameResource
                        && (action != WishActionType.START_PREDEFINED_EVENT
                        || existing.featureName().equals(feature))
                        && (type != null || action == WishActionType.START_PREDEFINED_EVENT
                        || WishActionPolicy.isTrustedBuiltin(existing.reference()))) return existing;
            }
            if (values.size() >= CapabilityCatalog.MAX_CANDIDATES) throw invalid(WishPlanError.INVALID_CANDIDATE);
            String id = "candidate-%03d".formatted(values.size() + 1);
            VerifiedRegistryResource verified = type == null ? null : new VerifiedRegistryResource(type, resource);
            String namespace = verified == null ? "minecraft" : resource.substring(0, resource.indexOf(':'));
            CandidateSourceKind source = action == WishActionType.START_PREDEFINED_EVENT
                    ? CandidateSourceKind.MOD_FEATURE
                    : verified == null ? CandidateSourceKind.VANILLA_BUILTIN
                    : namespace.equals("minecraft") ? CandidateSourceKind.VANILLA_REGISTRY
                    : CandidateSourceKind.MOD_FEATURE;
            String sourceMod = action == WishActionType.START_PREDEFINED_EVENT ? WishingWillow.MOD_ID : namespace;
            String sourceVersion = sourceMod.equals("minecraft") ? "1.20.1" : "";
            CapabilityCandidate created = new CapabilityCandidate(id, capability, capability, MatchType.EXACT,
                    source, sourceMod, sourceMod, sourceVersion, feature, feature(type, action), verified,
                    "Direct Action DSL server-validated candidate", KnowledgeLevel.VERIFIED,
                    1, 1, 0, 100, CapabilityMatcher.risk(capability), 100);
            values.add(created);
            return created;
        }

        private CapabilityCatalog catalog() {
            EnumMap<WishCapability, List<CapabilityCandidate>> grouped = new EnumMap<>(WishCapability.class);
            values.forEach(candidate -> grouped.computeIfAbsent(candidate.requestedCapability(), ignored -> new ArrayList<>())
                    .add(candidate));
            List<CapabilityMatchSet> sets = new ArrayList<>();
            grouped.forEach((capability, candidates) -> sets.add(new CapabilityMatchSet(capability,
                    candidates.isEmpty() ? MatchType.UNSATISFIED : candidates.get(0).matchType(), candidates)));
            return CapabilityCatalog.create(sets, values, initial.knowledgeState(),
                    initial.knowledgeDigest(), registry.digest());
        }

        private static FeatureType feature(RegistryEntryType type, WishActionType action) {
            if (type == null) return switch (action) {
                case APPLY_EFFECT_CATEGORY, CLEAR_EFFECTS, MODIFY_ATTRIBUTE -> FeatureType.PLAYER_SYSTEM;
                case CHANGE_WEATHER -> FeatureType.WEATHER;
                case START_PREDEFINED_EVENT -> FeatureType.WORLD_SYSTEM;
                default -> FeatureType.WORLD_SYSTEM;
            };
            return switch (type) {
                case ITEM -> FeatureType.ITEM;
                case BLOCK -> FeatureType.BLOCK;
                case ENTITY -> FeatureType.ENTITY;
                case EFFECT -> FeatureType.EFFECT;
                case SOUND -> FeatureType.SOUND;
                case DIMENSION -> FeatureType.DIMENSION;
                case STRUCTURE -> FeatureType.STRUCTURE;
                default -> FeatureType.UNKNOWN;
            };
        }
    }
}
