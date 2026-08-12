package com.ikunkk02.wishingwillow.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.ikunkk02.wishingwillow.program.ValidatedWishProgram;
import com.ikunkk02.wishingwillow.program.WishProgramAction;

import java.util.List;
import java.util.Set;

/**
 * Final server-authoritative decision gate for structured AI output.
 * The AI decision is advisory: only this policy emits a final decision.
 */
public final class WishDecisionPolicy {
    private static final int MAX_EXPANDED_ACTIONS = 128;
    private static final int MAX_ENTITY_SPAWNS = 64;
    private static final int MAX_ITEM_COUNT = 4096;
    private static final int MAX_AFFECTED_ENTITIES = 64;
    private static final int MAX_TEMPORARY_BEHAVIOR_SECONDS = 86_400;
    private static final Set<String> FORBIDDEN_ACTIONS = Set.of(
            "run_command", "execute_command", "run_code", "shell", "http_request",
            "file_access", "permission_grant", "operator_player");

    public Result evaluate(WishDecision aiDecision, WishRejection aiRejection,
                           ValidatedWishProgram validatedProgram) {
        if (aiDecision == null) return rejected(Code.INVALID_AI_DECISION, WishRejectionCode.SERVER_POLICY);
        if (aiDecision == WishDecision.REJECT) {
            if (aiRejection == null) return rejected(Code.INVALID_AI_REJECTION, WishRejectionCode.SERVER_POLICY);
            return new Result(FinalDecision.FINAL_REJECTED, Code.AI_REJECTED, aiRejection.code());
        }
        if (aiRejection != null || validatedProgram == null || validatedProgram.program() == null) {
            return rejected(Code.INVALID_ACCEPT_SHAPE, WishRejectionCode.SERVER_POLICY);
        }

        Budget budget = new Budget();
        List<WishProgramAction> actions = java.util.stream.Stream.concat(
                validatedProgram.program().coreActions().stream(),
                validatedProgram.program().presentationActions().stream()).toList();
        for (WishProgramAction action : actions) {
            Code violation = inspect(action.action(), action.parameters(), 1, budget);
            if (violation != null) {
                WishRejectionCode rejection = violation == Code.RESOURCE_ABUSE
                        ? WishRejectionCode.RESOURCE_ABUSE : WishRejectionCode.UNSAFE_SERVER_OPERATION;
                return rejected(violation, rejection);
            }
        }
        return new Result(FinalDecision.FINAL_ACCEPTED, Code.ACCEPTED, WishRejectionCode.NONE);
    }

    private static Code inspect(String id, JsonObject parameters, int multiplier, Budget budget) {
        if (FORBIDDEN_ACTIONS.contains(id)) return Code.FORBIDDEN_ACTION;
        if (multiplier < 1 || multiplier > MAX_EXPANDED_ACTIONS) return Code.RESOURCE_ABUSE;
        if ("repeat".equals(id)) {
            Integer count = integer(parameters, "count");
            if (count == null || count < 1 || count > 16) return Code.RESOURCE_ABUSE;
            JsonElement children = parameters.get("actions");
            if (children == null || !children.isJsonArray()) return Code.INVALID_PROGRAM_SHAPE;
            for (JsonElement child : children.getAsJsonArray()) {
                if (!child.isJsonObject()) return Code.INVALID_PROGRAM_SHAPE;
                JsonObject object = child.getAsJsonObject();
                if (!object.has("action") || !object.get("action").isJsonPrimitive()) {
                    return Code.INVALID_PROGRAM_SHAPE;
                }
                JsonObject childParameters = object.has("parameters") && object.get("parameters").isJsonObject()
                        ? object.getAsJsonObject("parameters") : new JsonObject();
                Code violation = inspect(object.get("action").getAsString(), childParameters,
                        multiplier * count, budget);
                if (violation != null) return violation;
            }
            return null;
        }
        if ("sequence".equals(id) || "parallel".equals(id)) {
            JsonElement children = parameters.get("actions");
            if (children == null || !children.isJsonArray()) return Code.INVALID_PROGRAM_SHAPE;
            JsonArray array = children.getAsJsonArray();
            for (JsonElement child : array) {
                if (!child.isJsonObject()) return Code.INVALID_PROGRAM_SHAPE;
                JsonObject object = child.getAsJsonObject();
                if (!object.has("action") || !object.get("action").isJsonPrimitive()) return Code.INVALID_PROGRAM_SHAPE;
                JsonObject childParameters = object.has("parameters") && object.get("parameters").isJsonObject()
                        ? object.getAsJsonObject("parameters") : new JsonObject();
                Code violation = inspect(object.get("action").getAsString(), childParameters, multiplier, budget);
                if (violation != null) return violation;
            }
            return null;
        }

        budget.expandedActions += multiplier;
        if (budget.expandedActions > MAX_EXPANDED_ACTIONS) return Code.RESOURCE_ABUSE;
        if ("spawn_entity".equals(id) && exceeds(parameters, "count", MAX_ENTITY_SPAWNS, multiplier)) {
            return Code.RESOURCE_ABUSE;
        }
        if (("give_item".equals(id) || "spawn_item_rain".equals(id))
                && exceeds(parameters, "count", MAX_ITEM_COUNT, multiplier)) return Code.RESOURCE_ABUSE;
        if (("follow_player".equals(id) || "avoid_player".equals(id))
                && (exceeds(parameters, "max_entities", MAX_AFFECTED_ENTITIES, multiplier)
                || temporaryDurationAbusive(parameters))) return Code.RESOURCE_ABUSE;
        if (("entity_suppression".equals(id) || "remove_entity".equals(id))
                && exceeds(parameters, "max_count", MAX_AFFECTED_ENTITIES, multiplier)) {
            return Code.RESOURCE_ABUSE;
        }
        return null;
    }

    private static boolean temporaryDurationAbusive(JsonObject parameters) {
        boolean permanent = parameters.has("permanent") && parameters.get("permanent").isJsonPrimitive()
                && parameters.get("permanent").getAsBoolean();
        Integer duration = integer(parameters, "duration_seconds");
        return !permanent && duration != null && (duration < 1 || duration > MAX_TEMPORARY_BEHAVIOR_SECONDS);
    }

    private static boolean exceeds(JsonObject parameters, String key, int maximum, int multiplier) {
        Integer value = integer(parameters, key);
        if (value == null) return false;
        return value < 1 || (long) value * multiplier > maximum;
    }

    private static Integer integer(JsonObject parameters, String key) {
        if (!parameters.has(key) || !parameters.get(key).isJsonPrimitive()
                || !parameters.get(key).getAsJsonPrimitive().isNumber()) return null;
        try { return parameters.get(key).getAsInt(); }
        catch (RuntimeException ignored) { return null; }
    }

    private static Result rejected(Code code, WishRejectionCode rejectionCode) {
        return new Result(FinalDecision.FINAL_REJECTED, code, rejectionCode);
    }

    public enum FinalDecision { FINAL_ACCEPTED, FINAL_REJECTED }

    public enum Code {
        ACCEPTED,
        AI_REJECTED,
        INVALID_AI_DECISION,
        INVALID_AI_REJECTION,
        INVALID_ACCEPT_SHAPE,
        INVALID_PROGRAM_SHAPE,
        FORBIDDEN_ACTION,
        RESOURCE_ABUSE
    }

    public record Result(FinalDecision decision, Code code, WishRejectionCode rejectionCode) {
        public boolean accepted() { return decision == FinalDecision.FINAL_ACCEPTED; }
    }

    private static final class Budget { private int expandedActions; }
}
