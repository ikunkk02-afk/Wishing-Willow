package com.ikunkk02.wishingwillow.planning;

import com.ikunkk02.wishingwillow.ai.WishCapability;

import java.util.Set;

public record WishPlanValidation(WishPlanDraft draft, WishPlanState state,
                                 Set<WishCapability> unfulfilledCapabilities) {
    public WishPlanValidation { unfulfilledCapabilities = Set.copyOf(unfulfilledCapabilities); }
}
