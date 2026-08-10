package com.ikunkk02.wishingwillow.execution;

import com.ikunkk02.wishingwillow.planning.WishActionType;

import javax.annotation.Nullable;

public record WishExecutionValidationResult(
        boolean valid,
        WishExecutionAcceptError error,
        int stepIndex,
        @Nullable WishActionType action,
        String detail
) {
    public static WishExecutionValidationResult success() {
        return new WishExecutionValidationResult(true, WishExecutionAcceptError.NONE, -1, null, "");
    }

    public static WishExecutionValidationResult rejected(WishExecutionAcceptError error, int stepIndex,
                                                         @Nullable WishActionType action, String detail) {
        return new WishExecutionValidationResult(false, error, stepIndex, action, detail == null ? "" : detail);
    }
}
