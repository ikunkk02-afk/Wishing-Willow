package com.ikunkk02.wishingwillow.execution;

public enum WishStepExecutionState {
    PENDING, WAITING_DELAY, WAITING_TRIGGER, WAITING_TARGET, READY, RUNNING,
    SUCCEEDED, FAILED, SKIPPED, CANCELLED, STALE;

    public boolean terminal() {
        return this == SUCCEEDED || this == FAILED || this == SKIPPED
                || this == CANCELLED || this == STALE;
    }
}
