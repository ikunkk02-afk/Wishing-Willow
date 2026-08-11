package com.ikunkk02.wishingwillow.contract;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.ikunkk02.wishingwillow.ai.WishInterpretation;
import com.ikunkk02.wishingwillow.planning.WishActionType;
import com.ikunkk02.wishingwillow.planning.WishPlanDraft;
import com.ikunkk02.wishingwillow.planning.WishPlanStep;
import com.ikunkk02.wishingwillow.execution.WishExecutionRecord;
import com.ikunkk02.wishingwillow.execution.PredefinedWishEventRegistry;

import java.util.List;
import java.util.Locale;

/** Proves that a legal-looking plan actually grants the non-negotiable outcome. */
public final class WishContractValidator {
    private WishContractValidator() {}

    public static WishContractValidation validate(WishInterpretation interpretation, WishPlanDraft plan) {
        return validate(interpretation, plan.steps());
    }

    public static WishContractValidation validate(WishInterpretation interpretation, List<WishPlanStep> steps) {
        if (interpretation.schemaVersion() < 2) return fulfilled("LEGACY_SCHEMA", 0);
        WishContract contract = interpretation.contract();
        WishContractValidation machine = switch (contract.type()) {
            case OBTAIN_RESOURCE -> validateResource(contract, steps);
            case CREATE_STRUCTURE -> requireAction(steps, WishActionType.CREATE_STRUCTURE, "STRUCTURE_MISSING");
            case CHANGE_PLAYER_STATE, PERSISTENT_CONDITION -> validatePlayerState(contract, steps);
            case SOCIAL_RELATION -> validatePositiveNumber(steps, WishActionType.CHANGE_REPUTATION, "delta", "SOCIAL_RELATION_MISSING");
            case SPAWN_COMPANION -> validateCompanion(steps);
            case TRAVEL -> requireAction(steps, WishActionType.TELEPORT, "TRAVEL_MISSING");
            case REMOVE_THREAT -> validateThreatRemoval(steps);
            case CHANGE_WORLD_STATE -> validateWorldState(steps);
            case KNOWLEDGE, RESURRECTION, OTHER -> review("SEMANTIC_REVIEW_REQUIRED", 0);
        };
        if (machine.state() == WishContractValidationState.CONTRACT_NOT_FULFILLED) return machine;
        return contract.requiresAiReview() ? review("CUSTOM_SEMANTIC_REVIEW", machine.promisedQuantity()) : machine;
    }

    /** Reconciles machine-verifiable promises against persisted executor affected counts. */
    public static WishContractValidation validateActual(WishInterpretation interpretation,
                                                        List<WishPlanStep> steps,
                                                        WishExecutionRecord execution) {
        WishContractValidation promised = validate(interpretation, steps);
        if (promised.state() == WishContractValidationState.CONTRACT_NOT_FULFILLED) return promised;
        if (interpretation.schemaVersion() < 2 || interpretation.contract().type() != WishContractType.OBTAIN_RESOURCE) {
            return promised;
        }
        String semantic = interpretation.contract().semantic(WishConstraintKind.RESOURCE_SEMANTIC).orElse("");
        int minimum = interpretation.contract().quantity(WishConstraintKind.MINIMUM_QUANTITY).orElse(1);
        int actual = 0;
        for (WishPlanStep step : steps) {
            if (isResourceGrant(step.action()) && resourceMatches(step, semantic)) {
                var result = execution.step(step.stepIndex());
                if (result != null && result.state().name().equals("SUCCEEDED")) actual += Math.max(0, result.affected());
            }
        }
        return actual >= minimum ? fulfilled("ACTUAL_RESOURCE_QUANTITY_PROVEN", actual)
                : rejected("ACTUAL_RESOURCE_QUANTITY_SHORT", actual);
    }

    private static WishContractValidation validateResource(WishContract contract, List<WishPlanStep> steps) {
        String semantic = contract.semantic(WishConstraintKind.RESOURCE_SEMANTIC).orElse("");
        int minimum = contract.quantity(WishConstraintKind.MINIMUM_QUANTITY).orElse(1);
        if (semantic.isBlank() || minimum < 1) return rejected("MALFORMED_RESOURCE_CONTRACT", 0);
        int promised = 0;
        for (WishPlanStep step : steps) {
            if (!isResourceGrant(step.action()) || !resourceMatches(step, semantic)) continue;
            promised += switch (step.action()) {
                case GIVE_ITEM, PLACE_BLOCK_PATTERN -> integer(step.parameters(), "count", 0);
                case CHANGE_BLOCK -> 1;
                default -> 0;
            };
        }
        return promised >= minimum ? fulfilled("RESOURCE_QUANTITY_PROVEN", promised)
                : rejected("RESOURCE_QUANTITY_SHORT", promised);
    }

    private static boolean isResourceGrant(WishActionType type) {
        return type == WishActionType.GIVE_ITEM || type == WishActionType.CHANGE_BLOCK
                || type == WishActionType.PLACE_BLOCK_PATTERN;
    }

    private static boolean resourceMatches(WishPlanStep step, String semantic) {
        if (step.candidateReference() == null || step.candidateReference().registryResource() == null) return false;
        String id = step.candidateReference().registryResource().id();
        int separator = id.indexOf(':');
        String path = separator < 0 ? id : id.substring(separator + 1);
        return normalize(path).equals(normalize(semantic));
    }

    private static WishContractValidation validatePlayerState(WishContract contract, List<WishPlanStep> steps) {
        String metric = contract.semantic(WishConstraintKind.STATE_METRIC).orElse("");
        if (metric.equals("all_positive_status_effects")) {
            boolean exactBuiltin = steps.stream().anyMatch(step -> step.action() == WishActionType.START_PREDEFINED_EVENT
                    && step.candidateReference() != null
                    && PredefinedWishEventRegistry.ALL_POSITIVE_EFFECTS.equals(
                    step.candidateReference().featureName()));
            return exactBuiltin ? fulfilled("ALL_POSITIVE_STATUS_EFFECTS_PROVEN", 0)
                    : rejected("ALL_POSITIVE_STATUS_EFFECTS_MISSING", 0);
        }
        if (metric.equals("movement_speed") || metric.equals("speed")) {
            for (WishPlanStep step : steps) {
                if (step.action() == WishActionType.MODIFY_ATTRIBUTE
                        && "MOVEMENT_SPEED".equals(string(step.parameters(), "attribute"))
                        && number(step.parameters(), "amount", 0) > 0) {
                    return fulfilled("MOVEMENT_SPEED_INCREASE_PROVEN", 0);
                }
            }
            return rejected("MOVEMENT_SPEED_INCREASE_MISSING", 0);
        }
        boolean positive = steps.stream().anyMatch(step -> step.action() == WishActionType.MODIFY_ATTRIBUTE
                && number(step.parameters(), "amount", 0) > 0);
        return positive ? fulfilled("PLAYER_STATE_CHANGE_PROVEN", 0) : review("PLAYER_STATE_SEMANTIC_REVIEW", 0);
    }

    private static WishContractValidation validateCompanion(List<WishPlanStep> steps) {
        boolean spawn = steps.stream().anyMatch(step -> step.action() == WishActionType.SPAWN_ENTITY
                && integer(step.parameters(), "count", 0) > 0);
        boolean persist = steps.stream().anyMatch(step -> step.action() == WishActionType.FOLLOW_PLAYER)
                || steps.stream().anyMatch(step -> step.capability().name().equals("PERSISTENT_FOLLOWER"));
        return spawn && persist ? fulfilled("PERSISTENT_COMPANION_PROVEN", 0)
                : rejected("PERSISTENT_COMPANION_MISSING", 0);
    }

    private static WishContractValidation validateThreatRemoval(List<WishPlanStep> steps) {
        boolean removal = steps.stream().anyMatch(step -> step.action() == WishActionType.DESPAWN_ENTITY
                || step.action() == WishActionType.AVOID_PLAYER || step.action() == WishActionType.CHANGE_MOB_TARGET);
        return removal ? fulfilled("THREAT_REMOVAL_PROVEN", 0) : rejected("THREAT_REMOVAL_MISSING", 0);
    }

    private static WishContractValidation validateWorldState(List<WishPlanStep> steps) {
        boolean world = steps.stream().anyMatch(step -> step.action() == WishActionType.CHANGE_TIME
                || step.action() == WishActionType.CHANGE_WEATHER || step.action() == WishActionType.PLACE_BLOCK_PATTERN
                || step.action() == WishActionType.CREATE_STRUCTURE);
        return world ? fulfilled("WORLD_STATE_CHANGE_PROVEN", 0) : review("WORLD_STATE_SEMANTIC_REVIEW", 0);
    }

    private static WishContractValidation requireAction(List<WishPlanStep> steps, WishActionType action, String code) {
        return steps.stream().anyMatch(step -> step.action() == action) ? fulfilled(action.name() + "_PROVEN", 0) : rejected(code, 0);
    }

    private static WishContractValidation validatePositiveNumber(List<WishPlanStep> steps, WishActionType action, String key, String code) {
        return steps.stream().anyMatch(step -> step.action() == action && number(step.parameters(), key, 0) > 0)
                ? fulfilled(action.name() + "_POSITIVE_PROVEN", 0) : rejected(code, 0);
    }

    private static int integer(JsonObject object, String key, int fallback) {
        JsonElement value = object.get(key); if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) return fallback;
        try { return value.getAsInt(); } catch (RuntimeException ignored) { return fallback; }
    }
    private static double number(JsonObject object, String key, double fallback) {
        JsonElement value = object.get(key); if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) return fallback;
        try { return value.getAsDouble(); } catch (RuntimeException ignored) { return fallback; }
    }
    private static String string(JsonObject object, String key) {
        JsonElement value = object.get(key); return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() ? value.getAsString() : "";
    }
    private static String normalize(String value) { return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", ""); }
    private static WishContractValidation fulfilled(String code, int quantity) { return new WishContractValidation(WishContractValidationState.CONTRACT_FULFILLED, code, quantity); }
    private static WishContractValidation rejected(String code, int quantity) { return new WishContractValidation(WishContractValidationState.CONTRACT_NOT_FULFILLED, code, quantity); }
    private static WishContractValidation review(String code, int quantity) { return new WishContractValidation(WishContractValidationState.AI_REVIEW_REQUIRED, code, quantity); }
}
