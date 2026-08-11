package com.ikunkk02.wishingwillow.execution;

import java.util.List;

/** Core actions determine success; optional presentation failures never overturn it. */
public final class WishProgramResultPolicy {
    private WishProgramResultPolicy() { }

    public static WishExecutionState reduce(List<WishStepExecutionState> core,
                                            List<WishStepExecutionState> presentation) {
        if (core.isEmpty()) return WishExecutionState.FAILED;
        if (core.stream().anyMatch(state -> state == WishStepExecutionState.FAILED
                || state == WishStepExecutionState.STALE || state == WishStepExecutionState.CANCELLED)) {
            return WishExecutionState.FAILED;
        }
        if (core.stream().anyMatch(state -> !state.terminal())) return WishExecutionState.RUNNING;
        return WishExecutionState.COMPLETED;
    }
}
