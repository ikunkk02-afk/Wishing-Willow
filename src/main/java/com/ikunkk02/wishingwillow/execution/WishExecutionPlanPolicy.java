package com.ikunkk02.wishingwillow.execution;

import com.ikunkk02.wishingwillow.ai.WishCapability;
import com.ikunkk02.wishingwillow.planning.WishPlanState;

import java.util.List;
import java.util.Set;

/** Keeps persisted plan-state checks aligned with the schema that produced the Wish Contract. */
public final class WishExecutionPlanPolicy {
    private WishExecutionPlanPolicy() {
    }

    public static boolean readyHasBlockingUnfulfilledCapabilities(
            int interpretationSchemaVersion,
            WishPlanState state,
            Set<WishCapability> unfulfilledCapabilities
    ) {
        return state == WishPlanState.READY
                && interpretationSchemaVersion < 2
                && !unfulfilledCapabilities.isEmpty();
    }

    public static boolean partialMissesPrimaryCapability(
            WishPlanState state,
            List<WishCapability> requiredCapabilities,
            Set<WishCapability> unfulfilledCapabilities
    ) {
        return state == WishPlanState.PARTIAL
                && (requiredCapabilities.isEmpty()
                || unfulfilledCapabilities.contains(requiredCapabilities.get(0)));
    }
}
