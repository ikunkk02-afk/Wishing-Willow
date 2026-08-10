package com.ikunkk02.wishingwillow.execution;

public enum WishExecutionState {
    NOT_ACCEPTED, VALIDATING, SCHEDULED, WAITING_TRIGGER, RUNNING,
    COMPLETED, PARTIAL, FAILED, CANCELLED, STALE;

    public boolean terminal() {
        return this == COMPLETED || this == PARTIAL || this == FAILED
                || this == CANCELLED || this == STALE;
    }
}
