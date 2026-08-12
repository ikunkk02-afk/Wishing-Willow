package com.ikunkk02.wishingwillow.planning;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.internal.Streams;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.ikunkk02.wishingwillow.ai.WishCapability;
import com.ikunkk02.wishingwillow.ai.WishDelivery;
import com.ikunkk02.wishingwillow.ai.WishInterpretation;
import com.ikunkk02.wishingwillow.execution.ExecutionSettingsSnapshot;
import com.ikunkk02.wishingwillow.execution.WishActionPolicy;
import com.ikunkk02.wishingwillow.execution.WishExecutionAcceptError;
import com.ikunkk02.wishingwillow.execution.WishPolicyDecision;
import com.ikunkk02.wishingwillow.execution.WishSafetyPolicy;
import com.ikunkk02.wishingwillow.research.RegistryEntryType;
import com.ikunkk02.wishingwillow.contract.WishContractValidator;
import com.ikunkk02.wishingwillow.contract.WishContractValidationState;
import com.ikunkk02.wishingwillow.ai.WishRefusalGuard;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.io.StringReader;

/**
 * @deprecated Legacy WishPlan compatibility only. Do not use for WishProgram execution.
 */
@Deprecated
public final class WishPlanValidator {
    public static final int MAX_SUMMARY = 1024;
    public static final int MAX_REASON = 512;
    public static final int MAX_AI_JSON = 512 * 1024;
    public static final int MAX_RAW_STEPS = 512;
    private static final Set<String> ROOT_FIELDS = Set.of(
            "schema_version", "summary", "delivery", "severity", "estimated_duration", "steps");
    private static final Set<String> STEP_FIELDS = Set.of(
            "step_index", "timing", "delay_seconds", "trigger", "action", "capability",
            "candidate_id", "target", "parameters", "selection_reason");
    private static final Set<String> BATCH_STEP_FIELDS = Set.of(
            "step_index", "timing", "delay_seconds", "trigger", "action", "capability",
            "candidate_id", "target", "parameters", "selection_reason", "batch_id");

    private WishPlanValidator() { }

    public static WishPlanValidation parseAndValidate(String raw, WishInterpretation interpretation,
                                                      CapabilityCatalog catalog, PlanningEnvironment environment) {
        return parseAndValidate(raw, interpretation, catalog, environment,
                ExecutionSettingsSnapshot.permissive());
    }

    public static WishPlanValidation parseAndValidate(String raw, WishInterpretation interpretation,
                                                      CapabilityCatalog catalog, PlanningEnvironment environment,
                                                      ExecutionSettingsSnapshot settings) {
        if (raw == null || raw.length() > MAX_AI_JSON) throw invalid(WishPlanError.INVALID_JSON);
        final JsonObject root;
        try {
            JsonElement parsed = parseStrict(stripFence(raw));
            if (!parsed.isJsonObject()) throw invalid(WishPlanError.INVALID_JSON);
            root = parsed.getAsJsonObject();
        } catch (RuntimeException exception) {
            if (exception instanceof IllegalArgumentException illegal) throw illegal;
            throw invalid(WishPlanError.INVALID_JSON);
        }
        int schemaVersion = integer(root, "schema_version");
        if (!root.keySet().equals(ROOT_FIELDS) || (schemaVersion != 1 && schemaVersion != 2)) {
            throw invalid(WishPlanError.INVALID_JSON);
        }
        String summary = string(root, "summary", MAX_SUMMARY);
        if (WishRefusalGuard.containsRefusal(summary)) throw invalid(WishPlanError.REFUSAL_RESPONSE);
        WishDelivery delivery = enumValue(root, "delivery", WishDelivery.class);
        int severity = integer(root, "severity");
        if (severity != interpretation.severity() || delivery != interpretation.delivery()) {
            throw invalid(WishPlanError.DELIVERY_CONFLICT);
        }
        WishEstimatedDuration duration = enumValue(root, "estimated_duration", WishEstimatedDuration.class);
        JsonArray array = root.getAsJsonArray("steps");
        int rawLimit = schemaVersion == 1 ? WishPlanBudget.maxSteps(severity) : MAX_RAW_STEPS;
        if (array == null || array.size() < 1 || array.size() > rawLimit) {
            throw invalid(WishPlanError.BUDGET_EXCEEDED);
        }
        List<WishPlanStep> steps = new ArrayList<>();
        Set<WishCapability> covered = EnumSet.noneOf(WishCapability.class);
        Set<String> unique = new HashSet<>();
        for (int index = 0; index < array.size(); index++) {
            JsonElement element = array.get(index);
            if (!element.isJsonObject() || !(element.getAsJsonObject().keySet().equals(STEP_FIELDS)
                    || element.getAsJsonObject().keySet().equals(BATCH_STEP_FIELDS))) {
                throw invalid(WishPlanError.INVALID_JSON);
            }
            JsonObject step = element.getAsJsonObject();
            if (integer(step, "step_index") != index) throw invalid(WishPlanError.INVALID_PARAMETER);
            WishStepTiming timing = enumValue(step, "timing", WishStepTiming.class);
            int delay = integer(step, "delay_seconds");
            WishTriggerType trigger = enumValue(step, "trigger", WishTriggerType.class);
            validateTiming(timing, delay, trigger);
            WishActionType action = enumValue(step, "action", WishActionType.class);
            WishCapability capability = enumValue(step, "capability", WishCapability.class);
            boolean optionalPresentation = step.has("selection_reason")
                    && "Validated optional absurd presentation.".equals(step.get("selection_reason").getAsString());
            if (!WishContractCapabilityDeriver.allows(interpretation, capability) && !optionalPresentation) {
                throw invalid(WishPlanError.INVALID_CANDIDATE);
            }
            String candidateId = string(step, "candidate_id", 32);
            CapabilityCandidate candidate = catalog.find(candidateId);
            if (candidate == null || candidate.requestedCapability() != capability) {
                throw invalid(WishPlanError.INVALID_CANDIDATE);
            }
            validateCandidateEnvironment(candidate, environment);
            WishTargetType target = enumValue(step, "target", WishTargetType.class);
            JsonObject parameters = step.getAsJsonObject("parameters");
            if (parameters == null) throw invalid(WishPlanError.INVALID_PARAMETER);
            WishPolicyDecision actionDecision = WishActionPolicy.validate(candidate.reference(), action,
                    parameters, target, timing, delay, trigger, severity);
            if (!actionDecision.allowed()) throw policyInvalid(actionDecision.error());
            String reason = string(step, "selection_reason", MAX_REASON);
            String batchId = step.has("batch_id") ? string(step, "batch_id", 64) : "";
            if (WishRefusalGuard.containsRefusal(reason)) throw invalid(WishPlanError.REFUSAL_RESPONSE);
            String signature = action + "|" + candidateId + "|" + timing + "|" + delay + "|" + trigger
                    + "|" + parameters;
            if (!unique.add(signature) && batchId.isBlank() && action != WishActionType.GIVE_ITEM
                    && action != WishActionType.REMOVE_ITEM) throw invalid(WishPlanError.INVALID_PARAMETER);
            WishPlanStep planned = new WishPlanStep(index, timing, delay, trigger, action, capability, candidateId,
                    target, parameters, reason, candidate.reference(), batchId);
            WishPolicyDecision safetyDecision = WishSafetyPolicy.validate(planned, severity, settings);
            if (!safetyDecision.allowed()) throw policyInvalid(safetyDecision.error());
            steps.add(planned);
            covered.add(capability);
        }
        if (WishPlanBudget.logicalSteps(steps) > WishPlanBudget.maxSteps(severity)) {
            throw invalid(WishPlanError.BUDGET_EXCEEDED);
        }
        validateBatches(steps);
        validateDelivery(delivery, steps);
        int destructive = steps.stream().mapToInt(WishPlanBudget::destructiveCost).sum();
        if (destructive > WishPlanBudget.maxDestructiveCost(severity)) {
            throw invalid(WishPlanError.BUDGET_EXCEEDED);
        }
        WishPlanDraft draft = new WishPlanDraft(schemaVersion, summary, delivery, severity, duration, steps);
        var contractValidation = WishContractValidator.validate(interpretation, draft, environment);
        if (contractValidation.state() == WishContractValidationState.CONTRACT_NOT_FULFILLED) {
            throw invalid(WishPlanError.CONTRACT_NOT_FULFILLED);
        }
        Set<WishCapability> unfulfilled = EnumSet.copyOf(interpretation.requiredCapabilities());
        unfulfilled.removeAll(covered);
        WishPlanState state = interpretation.schemaVersion() >= 2 || unfulfilled.isEmpty()
                ? WishPlanState.READY : WishPlanState.PARTIAL;
        return new WishPlanValidation(draft, state, unfulfilled);
    }

    public static void validateStored(WishPlan plan, PlanningEnvironment environment) {
        validateStored(plan, environment, ExecutionSettingsSnapshot.permissive());
    }

    public static void validateStored(WishPlan plan, PlanningEnvironment environment,
                                      ExecutionSettingsSnapshot settings) {
        if (plan.steps().size() > MAX_RAW_STEPS
                || WishPlanBudget.logicalSteps(plan.steps()) > WishPlanBudget.maxSteps(plan.severity())) {
            throw invalid(WishPlanError.BUDGET_EXCEEDED);
        }
        validateBatches(plan.steps());
        int destructive = 0;
        for (WishPlanStep step : plan.steps()) {
            CandidateReference reference = step.candidateReference();
            if (!environment.modPresent(reference.sourceModId(), reference.sourceModVersion())) {
                throw invalid(WishPlanError.MISSING_MOD);
            }
            if (reference.registryResource() != null) {
                if (!environment.contains(reference.registryResource().type(), reference.registryResource().id())) {
                    throw invalid(WishPlanError.STALE_RESOURCE);
                }
            }
            WishPolicyDecision actionDecision = WishActionPolicy.validate(reference, step.action(),
                    step.parameters(), step.target(), step.timing(), step.delaySeconds(), step.trigger(),
                    plan.severity());
            if (!actionDecision.allowed()) throw policyInvalid(actionDecision.error());
            WishPolicyDecision safetyDecision = WishSafetyPolicy.validate(step, plan.severity(), settings);
            if (!safetyDecision.allowed()) throw policyInvalid(safetyDecision.error());
            destructive += WishPlanBudget.destructiveCost(step);
        }
        if (destructive > WishPlanBudget.maxDestructiveCost(plan.severity())) {
            throw invalid(WishPlanError.BUDGET_EXCEEDED);
        }
    }

    private static void validateBatches(List<WishPlanStep> steps) {
        java.util.Map<String, WishPlanStep> first = new java.util.HashMap<>();
        java.util.Map<String, java.util.Set<String>> resources = new java.util.HashMap<>();
        for (WishPlanStep step : steps) {
            if (step.batchId().isBlank()) continue;
            if (!Set.of(WishActionType.GIVE_ITEM, WishActionType.REMOVE_ITEM,
                    WishActionType.APPLY_EFFECT, WishActionType.REMOVE_EFFECT).contains(step.action())) {
                throw invalid(WishPlanError.INVALID_PARAMETER);
            }
            WishPlanStep prior = first.putIfAbsent(step.batchId(), step);
            if (prior != null && (prior.action() != step.action()
                    || prior.capability() != step.capability()
                    || prior.timing() != step.timing()
                    || prior.delaySeconds() != step.delaySeconds()
                    || prior.trigger() != step.trigger()
                    || prior.target() != step.target())) {
                throw invalid(WishPlanError.INVALID_PARAMETER);
            }
            if ((step.action() == WishActionType.GIVE_ITEM || step.action() == WishActionType.REMOVE_ITEM)
                    && prior != null && !prior.candidateId().equals(step.candidateId())) {
                throw invalid(WishPlanError.INVALID_PARAMETER);
            }
            if ((step.action() == WishActionType.APPLY_EFFECT || step.action() == WishActionType.REMOVE_EFFECT)
                    && !resources.computeIfAbsent(step.batchId(), ignored -> new HashSet<>()).add(step.candidateId())) {
                throw invalid(WishPlanError.INVALID_PARAMETER);
            }
        }
    }

    private static void validateCandidateEnvironment(CapabilityCandidate candidate,
                                                     PlanningEnvironment environment) {
        if (!environment.modLoaded(candidate.sourceModId(), candidate.sourceModVersion())) {
            throw invalid(WishPlanError.MISSING_MOD);
        }
        if (candidate.registryResource() != null
                && !environment.contains(candidate.registryResource().type(), candidate.registryResource().id())) {
            throw invalid(WishPlanError.INVALID_REGISTRY);
        }
    }

    private static boolean supportsAction(WishCapability capability, WishActionType action) {
        return switch (action) {
            case GIVE_ITEM -> capability == WishCapability.GIVE_ITEM || capability == WishCapability.STRONG_WEAPON
                    || capability == WishCapability.INVENTORY_CHANGE;
            case REMOVE_ITEM -> capability == WishCapability.REMOVE_ITEM || capability == WishCapability.INVENTORY_CHANGE;
            case ITEM_RAIN -> capability == WishCapability.GIVE_ITEM
                    || capability == WishCapability.INVENTORY_CHANGE
                    || capability == WishCapability.WORLD_EVENT;
            case SPAWN_ENTITY -> Set.of(WishCapability.SPAWN_ENTITY, WishCapability.HOSTILE_ENTITY,
                    WishCapability.FRIENDLY_ENTITY, WishCapability.STALKING_ENTITY,
                    WishCapability.PERSISTENT_FOLLOWER, WishCapability.MIMIC_ENTITY,
                    WishCapability.POWERFUL_ENEMY, WishCapability.ENTITY_RECREATION).contains(capability);
            case DESPAWN_ENTITY -> Set.of(WishCapability.SPAWN_ENTITY, WishCapability.HOSTILE_ENTITY,
                    WishCapability.FRIENDLY_ENTITY, WishCapability.STALKING_ENTITY,
                    WishCapability.PERSISTENT_FOLLOWER, WishCapability.MIMIC_ENTITY).contains(capability);
            case APPLY_EFFECT, APPLY_EFFECT_CATEGORY -> Set.of(WishCapability.POWER_BUFF, WishCapability.POWER_DEBUFF,
                    WishCapability.HEALING, WishCapability.DAMAGE, WishCapability.DARKNESS,
                    WishCapability.IMMORTALITY).contains(capability);
            case REMOVE_EFFECT, CLEAR_EFFECTS -> Set.of(WishCapability.POWER_BUFF, WishCapability.POWER_DEBUFF,
                    WishCapability.DARKNESS).contains(capability);
            case TELEPORT -> Set.of(WishCapability.TELEPORT, WishCapability.DIMENSION_TRAVEL,
                    WishCapability.SPACE_TRAVEL, WishCapability.SPACECRAFT).contains(capability);
            case CHANGE_TIME -> capability == WishCapability.CHANGE_TIME || capability == WishCapability.WORLD_EVENT;
            case CHANGE_WEATHER -> capability == WishCapability.CHANGE_WEATHER || capability == WishCapability.WORLD_EVENT;
            case PLAY_SOUND -> capability == WishCapability.SOUND_EVENT;
            case SPAWN_PARTICLE -> capability == WishCapability.VISUAL_EVENT || capability == WishCapability.HALLUCINATION;
            case LIGHTNING -> capability == WishCapability.LIGHTNING || capability == WishCapability.WORLD_EVENT;
            case EXPLOSION -> capability == WishCapability.EXPLOSION;
            case CHANGE_BLOCK, REPLACE_BLOCK_AREA, PLACE_BLOCK_PATTERN -> capability == WishCapability.BLOCK_CHANGE
                    || capability == WishCapability.STRUCTURE;
            case FALLING_BLOCK_SHOWER -> capability == WishCapability.GIVE_ITEM
                    || capability == WishCapability.INVENTORY_CHANGE
                    || capability == WishCapability.BLOCK_CHANGE
                    || capability == WishCapability.WORLD_EVENT;
            case CREATE_STRUCTURE -> capability == WishCapability.STRUCTURE;
            case MODIFY_HEALTH -> Set.of(WishCapability.HEALING, WishCapability.DAMAGE,
                    WishCapability.IMMORTALITY, WishCapability.POWER_BUFF, WishCapability.POWER_DEBUFF).contains(capability);
            case MODIFY_HUNGER -> capability == WishCapability.PLAYER_ATTRIBUTE
                    || capability == WishCapability.POWER_BUFF || capability == WishCapability.POWER_DEBUFF;
            case MODIFY_ATTRIBUTE -> capability == WishCapability.PLAYER_ATTRIBUTE
                    || capability == WishCapability.POWER_BUFF || capability == WishCapability.POWER_DEBUFF;
            case CHANGE_MOB_TARGET, FOLLOW_PLAYER, AVOID_PLAYER -> Set.of(WishCapability.MOB_BEHAVIOR,
                    WishCapability.STALKING_ENTITY, WishCapability.PERSISTENT_FOLLOWER,
                    WishCapability.FRIENDLY_ENTITY, WishCapability.HOSTILE_ENTITY).contains(capability);
            case CHANGE_REPUTATION -> capability == WishCapability.REPUTATION;
            case START_PREDEFINED_EVENT, ENTITY_ATTRACTION_AURA -> capability == WishCapability.WORLD_EVENT
                    || capability == WishCapability.MEMORY_RELATED_EVENT
                    || capability == WishCapability.POWER_BUFF;
        };
    }

    private static RegistryEntryType resourceType(WishActionType action) {
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

    private static void validateTiming(WishStepTiming timing, int delay, WishTriggerType trigger) {
        boolean valid = switch (timing) {
            case IMMEDIATE -> delay == 0 && trigger == WishTriggerType.NONE;
            case DELAYED -> delay >= 1 && delay <= 86400 && trigger == WishTriggerType.AFTER_DELAY;
            case TRIGGERED -> delay == 0 && trigger != WishTriggerType.NONE && trigger != WishTriggerType.AFTER_DELAY;
            case DELAYED_AFTER_TRIGGER -> delay >= 1 && delay <= 86400
                    && trigger != WishTriggerType.NONE && trigger != WishTriggerType.AFTER_DELAY;
        };
        if (!valid) throw invalid(WishPlanError.INVALID_PARAMETER);
    }

    private static void validateDelivery(WishDelivery delivery, List<WishPlanStep> steps) {
        boolean valid = switch (delivery) {
            case IMMEDIATE -> steps.get(0).timing() == WishStepTiming.IMMEDIATE
                    && steps.stream().noneMatch(step -> step.delaySeconds() > 60);
            case DELAYED -> steps.stream().anyMatch(step -> step.timing() == WishStepTiming.DELAYED
                    || step.timing() == WishStepTiming.DELAYED_AFTER_TRIGGER);
            case CONDITIONAL -> steps.stream().anyMatch(step -> step.trigger() != WishTriggerType.NONE
                    && step.trigger() != WishTriggerType.AFTER_DELAY);
            case PROGRESSIVE -> steps.size() >= 2 && steps.stream().anyMatch(step -> step.timing() != WishStepTiming.IMMEDIATE);
            case HIDDEN -> true;
        };
        if (!valid) throw invalid(WishPlanError.DELIVERY_CONFLICT);
    }

    private static void validateParameters(WishActionType action, JsonObject p, int severity,
                                           CapabilityCandidate candidate, WishTargetType target) {
        switch (action) {
            case GIVE_ITEM, REMOVE_ITEM -> { keys(p, Set.of("count"), Set.of("count")); rangeInt(p, "count", 1, 64); }
            case SPAWN_ENTITY -> { keys(p, Set.of("count", "distance_min", "distance_max"), Set.of("count", "distance_min", "distance_max")); distance(p, 128); rangeInt(p, "count", 1, 10); }
            case DESPAWN_ENTITY -> { keys(p, Set.of("radius", "max_count"), Set.of("radius", "max_count")); rangeInt(p,"radius",2,64); rangeInt(p,"max_count",1,32); }
            case APPLY_EFFECT -> { keys(p, Set.of("duration_seconds", "amplifier"), Set.of("duration_seconds", "amplifier")); rangeInt(p,"duration_seconds",1,3600); rangeInt(p,"amplifier",0,4); }
            case REMOVE_EFFECT -> keys(p, Set.of(), Set.of());
            case CLEAR_EFFECTS -> keys(p, Set.of(), Set.of());
            case APPLY_EFFECT_CATEGORY -> { keys(p, Set.of("category","duration_seconds","amplifier"), Set.of("category","duration_seconds","amplifier")); oneOf(p,"category",Set.of("BENEFICIAL","HARMFUL","NEUTRAL")); rangeInt(p,"duration_seconds",1,3600); rangeInt(p,"amplifier",0,4); }
            case TELEPORT -> { keys(p, Set.of("mode","distance_min","distance_max"), Set.of("mode")); String mode=oneOf(p,"mode",Set.of("NEARBY_SAFE","RANDOM_SAFE","CANDIDATE_DIMENSION")); if (!mode.equals("CANDIDATE_DIMENSION")) { keys(p,Set.of("mode","distance_min","distance_max"),Set.of("mode","distance_min","distance_max")); distance(p,4096); } else if (candidate.registryResource()==null || candidate.registryResource().type()!=RegistryEntryType.DIMENSION) throw invalid(WishPlanError.INVALID_ACTION); }
            case CHANGE_TIME -> { keys(p,Set.of("value"),Set.of("value")); oneOf(p,"value",Set.of("DAY","NIGHT","DAWN","DUSK")); }
            case CHANGE_WEATHER -> { keys(p,Set.of("weather","duration_seconds"),Set.of("weather","duration_seconds")); oneOf(p,"weather",Set.of("CLEAR","RAIN","THUNDER")); rangeInt(p,"duration_seconds",30,3600); }
            case PLAY_SOUND -> { keys(p,Set.of("volume","pitch","distance"),Set.of("volume","pitch","distance")); range(p,"volume",0.1,4); range(p,"pitch",0.5,2); rangeInt(p,"distance",2,128); }
            case SPAWN_PARTICLE -> { keys(p,Set.of("count","radius"),Set.of("count","radius")); rangeInt(p,"count",1,512); range(p,"radius",0,32); }
            case LIGHTNING -> { keys(p,Set.of("count","distance_min","distance_max"),Set.of("count","distance_min","distance_max")); rangeInt(p,"count",1,4); distance(p,64); }
            case EXPLOSION -> { keys(p,Set.of("power","destroy_blocks","distance_min","distance_max"),Set.of("power","destroy_blocks","distance_min","distance_max")); range(p,"power",0.1,8); bool(p,"destroy_blocks"); distance(p,128); if (severity<41 || bool(p,"destroy_blocks")&&severity<61 || number(p,"power")>4&&severity<81) throw invalid(WishPlanError.BUDGET_EXCEEDED); }
            case CHANGE_BLOCK -> { keys(p,Set.of("distance_min","distance_max"),Set.of("distance_min","distance_max")); distance(p,64); }
            case REPLACE_BLOCK_AREA -> { keys(p,Set.of("radius","max_blocks"),Set.of("radius","max_blocks")); rangeInt(p,"radius",1,16); rangeInt(p,"max_blocks",1,2048); if(severity<41) throw invalid(WishPlanError.BUDGET_EXCEEDED); }
            case PLACE_BLOCK_PATTERN -> { keys(p,Set.of("pattern","count"),Set.of("pattern","count")); oneOf(p,"pattern",Set.of("ENCLOSURE","PILLAR","ROOM")); rangeInt(p,"count",1,2048); }
            case FALLING_BLOCK_SHOWER -> {
                keys(p, Set.of("count","spawn_height","radius","interval_ticks","landing_mode","spread"),
                        Set.of("count","spawn_height","radius","interval_ticks","landing_mode","spread"));
                rangeInt(p,"count",1,WishPlanBudget.MAX_FALLING_BLOCKS);
                rangeInt(p,"spawn_height",8,64); rangeInt(p,"radius",1,32);
                rangeInt(p,"interval_ticks",1,20);
                oneOf(p,"landing_mode",Set.of("PLACE","DROP_ITEM","PLACE_OR_DROP","DELIVER_TO_PLAYER"));
                oneOf(p,"spread",Set.of("RANDOM"));
            }
            case ITEM_RAIN -> {
                keys(p, Set.of("count","spawn_height","radius","interval_ticks","delivery_mode"),
                        Set.of("count","spawn_height","radius","interval_ticks","delivery_mode"));
                rangeInt(p,"count",1,WishPlanBudget.MAX_ITEM_UNITS);
                rangeInt(p,"spawn_height",8,64); rangeInt(p,"radius",1,32);
                rangeInt(p,"interval_ticks",1,20);
                oneOf(p,"delivery_mode",Set.of("WORLD_ITEMS","DELIVER_TO_PLAYER"));
            }
            case CREATE_STRUCTURE -> { keys(p,Set.of("template"),Set.of("template")); oneOf(p,"template",Set.of("SIMPLE_HOUSE")); }
            case MODIFY_HEALTH -> { keys(p,Set.of("delta","allow_lethal"),Set.of("delta","allow_lethal")); range(p,"delta",-40,40); if(bool(p,"allow_lethal")&&severity<81) throw invalid(WishPlanError.BUDGET_EXCEEDED); }
            case MODIFY_HUNGER -> { keys(p,Set.of("delta"),Set.of("delta")); rangeInt(p,"delta",-20,20); }
            case MODIFY_ATTRIBUTE -> { keys(p,Set.of("attribute","operation","amount","duration_seconds"),Set.of("attribute","operation","amount","duration_seconds")); oneOf(p,"attribute",Set.of("MAX_HEALTH","MOVEMENT_SPEED","ATTACK_DAMAGE","ARMOR","LUCK")); String op=oneOf(p,"operation",Set.of("ADD","MULTIPLY")); range(p,"amount",op.equals("ADD")?-20:-1,op.equals("ADD")?20:1); rangeInt(p,"duration_seconds",1,3600); }
            case CHANGE_MOB_TARGET -> { keys(p,Set.of("radius","max_entities","disposition"),Set.of("radius","max_entities","disposition")); rangeInt(p,"radius",2,64); rangeInt(p,"max_entities",1,32); oneOf(p,"disposition",Set.of("PLAYER","NEAREST_HOSTILE","CLEAR")); }
            case FOLLOW_PLAYER, AVOID_PLAYER -> { keys(p,Set.of("radius","max_entities","duration_seconds"),Set.of("radius","max_entities","duration_seconds")); rangeInt(p,"radius",2,64); rangeInt(p,"max_entities",1,16); rangeInt(p,"duration_seconds",1,3600); }
            case CHANGE_REPUTATION -> { keys(p,Set.of("delta","radius"),Set.of("delta","radius")); rangeInt(p,"delta",-100,100); rangeInt(p,"radius",2,64); }
            case START_PREDEFINED_EVENT -> { keys(p,Set.of("intensity"),Set.of("intensity")); rangeInt(p,"intensity",1,5); }
        }
        if ((candidate.providedCapability() == WishCapability.POWERFUL_ENEMY || candidate.riskScore() >= 85)
                && severity < 81) throw invalid(WishPlanError.BUDGET_EXCEEDED);
        if ((action == WishActionType.CHANGE_TIME || action == WishActionType.CHANGE_WEATHER) && target != WishTargetType.WORLD) {
            throw invalid(WishPlanError.INVALID_PARAMETER);
        }
    }

    private static void distance(JsonObject p, int max) {
        rangeInt(p,"distance_min",2,max); rangeInt(p,"distance_max",2,max);
        if (integer(p,"distance_min") > integer(p,"distance_max")) throw invalid(WishPlanError.INVALID_PARAMETER);
    }
    private static void keys(JsonObject p, Set<String> allowed, Set<String> required) {
        if (!allowed.containsAll(p.keySet()) || !p.keySet().containsAll(required)) throw invalid(WishPlanError.INVALID_PARAMETER);
    }
    private static void rangeInt(JsonObject p,String key,int min,int max) { int value=integer(p,key); if(value<min||value>max) throw invalid(WishPlanError.INVALID_PARAMETER); }
    private static void range(JsonObject p,String key,double min,double max) { double value=number(p,key); if(!Double.isFinite(value)||value<min||value>max) throw invalid(WishPlanError.INVALID_PARAMETER); }
    private static double number(JsonObject p,String key) { JsonElement e=p.get(key); if(e==null||!e.isJsonPrimitive()||!e.getAsJsonPrimitive().isNumber()) throw invalid(WishPlanError.INVALID_PARAMETER); return e.getAsDouble(); }
    private static boolean bool(JsonObject p,String key) { JsonElement e=p.get(key); if(e==null||!e.isJsonPrimitive()||!e.getAsJsonPrimitive().isBoolean()) throw invalid(WishPlanError.INVALID_PARAMETER); return e.getAsBoolean(); }
    private static String oneOf(JsonObject p,String key,Set<String> allowed) { String value=string(p,key,64); if(!allowed.contains(value)) throw invalid(WishPlanError.INVALID_PARAMETER); return value; }

    private static int integer(JsonObject object, String key) {
        JsonElement element=object.get(key); if(element==null||!element.isJsonPrimitive()||!element.getAsJsonPrimitive().isNumber()) throw invalid(WishPlanError.INVALID_JSON);
        String value=element.getAsString(); if(!value.matches("-?(0|[1-9][0-9]*)")) throw invalid(WishPlanError.INVALID_JSON);
        try { return Integer.parseInt(value); } catch(NumberFormatException exception) { throw invalid(WishPlanError.INVALID_JSON); }
    }
    private static String string(JsonObject object,String key,int max) { JsonElement e=object.get(key); if(e==null||!e.isJsonPrimitive()||!e.getAsJsonPrimitive().isString()) throw invalid(WishPlanError.INVALID_JSON); String value=e.getAsString().strip(); if(value.isEmpty()||value.length()>max) throw invalid(WishPlanError.INVALID_JSON); return value; }
    private static <E extends Enum<E>> E enumValue(JsonObject object,String key,Class<E> type) { try { return Enum.valueOf(type,string(object,key,64)); } catch(IllegalArgumentException exception) { throw invalid(WishPlanError.INVALID_JSON); } }
    private static String stripFence(String raw) { String value=raw.strip(); if(!value.startsWith("```")) return value; int newline=value.indexOf('\n'); if(newline<0||!value.endsWith("```")) throw invalid(WishPlanError.INVALID_JSON); return value.substring(newline+1,value.length()-3).strip(); }
    private static JsonElement parseStrict(String json) {
        try {
            JsonReader reader = new JsonReader(new StringReader(json));
            reader.setLenient(false);
            JsonElement value = Streams.parse(reader);
            if (reader.peek() != JsonToken.END_DOCUMENT) throw invalid(WishPlanError.INVALID_JSON);
            return value;
        } catch (RuntimeException exception) {
            if (exception instanceof IllegalArgumentException illegal) throw illegal;
            throw invalid(WishPlanError.INVALID_JSON);
        } catch (Exception exception) {
            throw invalid(WishPlanError.INVALID_JSON);
        }
    }
    private static IllegalArgumentException policyInvalid(WishExecutionAcceptError error) {
        WishPlanError mapped = switch (error) {
            case EXECUTION_DISABLED -> WishPlanError.EXECUTION_DISABLED;
            case INVALID_CANDIDATE -> WishPlanError.INVALID_CANDIDATE;
            case INVALID_RESOURCE, INVALID_ACTION_CAPABILITY -> WishPlanError.INVALID_ACTION;
            case STALE_RESOURCE -> WishPlanError.STALE_RESOURCE;
            case INVALID_PARAMETER -> WishPlanError.INVALID_PARAMETER;
            case INVALID_EVENT -> WishPlanError.INVALID_EVENT;
            case UNTRUSTED_REGISTRY_CANDIDATE -> WishPlanError.UNTRUSTED_REGISTRY_CANDIDATE;
            case UNSUPPORTED_ACTION -> WishPlanError.UNSUPPORTED_ACTION;
            case BUDGET_EXCEEDED -> WishPlanError.BUDGET_EXCEEDED;
            case RISK_TOO_HIGH -> WishPlanError.RISK_TOO_HIGH;
            case THIRD_PARTY_ENTITY_DISABLED -> WishPlanError.THIRD_PARTY_ENTITY_DISABLED;
            case THIRD_PARTY_ENTITY_SEVERITY -> WishPlanError.THIRD_PARTY_ENTITY_SEVERITY;
            case BLOCK_MODIFICATION_DISABLED -> WishPlanError.BLOCK_MODIFICATION_DISABLED;
            case EXPLOSIONS_DISABLED -> WishPlanError.EXPLOSIONS_DISABLED;
            case DESTRUCTIVE_EXPLOSIONS_DISABLED -> WishPlanError.DESTRUCTIVE_EXPLOSIONS_DISABLED;
            case CROSS_DIMENSION_TELEPORT_DISABLED -> WishPlanError.CROSS_DIMENSION_TELEPORT_DISABLED;
            case DESTRUCTIVE_SEVERITY_DISABLED -> WishPlanError.DESTRUCTIVE_SEVERITY_DISABLED;
            case DEBUG_SAFE_MODE -> WishPlanError.DEBUG_SAFE_MODE;
            default -> WishPlanError.UNKNOWN;
        };
        return invalid(mapped);
    }
    private static IllegalArgumentException invalid(WishPlanError error) { return new IllegalArgumentException(error.name()); }
}
