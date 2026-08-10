package com.ikunkk02.wishingwillow.planning;

import com.google.gson.JsonObject;
import com.ikunkk02.wishingwillow.ai.WishCapability;

public final class WishPlanBudget {
    private WishPlanBudget() { }

    public static int maxSteps(int severity) {
        if (severity <= 20) return 2;
        if (severity <= 40) return 3;
        if (severity <= 60) return 4;
        if (severity <= 80) return 6;
        if (severity <= 95) return 8;
        return 10;
    }

    public static int maxDestructiveCost(int severity) {
        if (severity <= 20) return 0;
        if (severity <= 40) return 2;
        if (severity <= 60) return 5;
        if (severity <= 80) return 10;
        if (severity <= 95) return 18;
        return 30;
    }

    public static int destructiveCost(WishPlanStep step) {
        JsonObject parameters = step.parameters();
        return switch (step.action()) {
            case SPAWN_ENTITY -> entityCost(step.candidateReference().providedCapability()) * integer(parameters, "count", 1);
            case LIGHTNING -> integer(parameters, "count", 1);
            case EXPLOSION -> {
                int base = (int) Math.ceil(decimal(parameters, "power", 0));
                yield bool(parameters, "destroy_blocks", false) ? base * 2 : base;
            }
            case CHANGE_BLOCK -> 1;
            case REPLACE_BLOCK_AREA -> Math.max(1, (int) Math.ceil(integer(parameters, "max_blocks", 1) / 128.0));
            case MODIFY_HEALTH -> {
                double delta = decimal(parameters, "delta", 0);
                yield delta < 0 ? (int) Math.ceil(-delta / 5.0) : 0;
            }
            case START_PREDEFINED_EVENT -> step.candidateReference().riskScore() >= 60
                    ? 2 * integer(parameters, "intensity", 1) : 0;
            default -> 0;
        };
    }

    private static int entityCost(WishCapability capability) {
        if (capability == WishCapability.POWERFUL_ENEMY) return 3;
        if (capability == WishCapability.HOSTILE_ENTITY) return 2;
        if (capability == WishCapability.STALKING_ENTITY || capability == WishCapability.PERSISTENT_FOLLOWER) return 1;
        return 0;
    }

    private static int integer(JsonObject object, String name, int fallback) {
        return object.has(name) ? object.get(name).getAsInt() : fallback;
    }

    private static double decimal(JsonObject object, String name, double fallback) {
        return object.has(name) ? object.get(name).getAsDouble() : fallback;
    }

    private static boolean bool(JsonObject object, String name, boolean fallback) {
        return object.has(name) ? object.get(name).getAsBoolean() : fallback;
    }
}
