package com.ikunkk02.wishingwillow.planning;

import javax.annotation.Nullable;

public record WishPlanResult(WishPlanState state, WishPlanError error, @Nullable WishPlanDraft draft) {
    public static WishPlanResult success(WishPlanDraft draft) {
        return new WishPlanResult(WishPlanState.VALIDATING, WishPlanError.NONE, draft);
    }

    public static WishPlanResult failed(WishPlanError error) {
        return new WishPlanResult(WishPlanState.FAILED, error, null);
    }
}
