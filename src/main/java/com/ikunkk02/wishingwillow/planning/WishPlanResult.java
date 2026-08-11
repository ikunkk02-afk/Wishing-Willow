package com.ikunkk02.wishingwillow.planning;

import javax.annotation.Nullable;

public record WishPlanResult(WishPlanState state, WishPlanError error, @Nullable WishPlanDraft draft,
                             int attemptsUsed) {
    public WishPlanResult {
        if (attemptsUsed < 1 || attemptsUsed > 3) throw new IllegalArgumentException("INVALID_ATTEMPT_COUNT");
    }

    public static WishPlanResult success(WishPlanDraft draft) {
        return success(draft, 1);
    }

    public static WishPlanResult success(WishPlanDraft draft, int attemptsUsed) {
        return new WishPlanResult(WishPlanState.VALIDATING, WishPlanError.NONE, draft, attemptsUsed);
    }

    public static WishPlanResult partial(WishPlanDraft draft) {
        return partial(draft, 1);
    }

    public static WishPlanResult partial(WishPlanDraft draft, int attemptsUsed) {
        return new WishPlanResult(WishPlanState.PARTIAL, WishPlanError.UNSATISFIED_CAPABILITIES,
                draft, attemptsUsed);
    }

    public static WishPlanResult failed(WishPlanError error) {
        return failed(error, 1);
    }

    public static WishPlanResult failed(WishPlanError error, int attemptsUsed) {
        return new WishPlanResult(WishPlanState.FAILED, error, null, attemptsUsed);
    }
}
