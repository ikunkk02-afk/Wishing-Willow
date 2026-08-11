package com.ikunkk02.wishingwillow.planning.direct;

import com.ikunkk02.wishingwillow.planning.CapabilityCatalog;
import com.ikunkk02.wishingwillow.planning.WishPlanError;
import com.ikunkk02.wishingwillow.planning.WishPlanResult;

import javax.annotation.Nullable;

public record DirectActionPlanningResult(
        State state,
        WishPlanResult result,
        @Nullable CapabilityCatalog catalog,
        @Nullable CompiledDirectActionPlan compiled,
        String detail
) {
    public enum State { SUCCESS, UNSUPPORTED_ACTION, FAILED }

    public static DirectActionPlanningResult success(CompiledDirectActionPlan compiled, int attempts) {
        return new DirectActionPlanningResult(State.SUCCESS,
                WishPlanResult.success(compiled.draft(), attempts), compiled.catalog(), compiled, "");
    }

    public static DirectActionPlanningResult unsupported(String detail, int attempts) {
        return new DirectActionPlanningResult(State.UNSUPPORTED_ACTION,
                WishPlanResult.failed(WishPlanError.UNSUPPORTED_ACTION, attempts), null, null, detail);
    }

    public static DirectActionPlanningResult failed(WishPlanError error, String detail, int attempts) {
        return new DirectActionPlanningResult(State.FAILED,
                WishPlanResult.failed(error, attempts), null, null, detail);
    }
}
