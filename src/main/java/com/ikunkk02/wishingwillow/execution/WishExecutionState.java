package com.ikunkk02.wishingwillow.execution;

public enum WishExecutionState {
    NOT_ACCEPTED, VALIDATING, SCHEDULED, WAITING_TRIGGER, RUNNING,
    COMPLETED, PARTIAL, UNEXECUTABLE, FAILED, CANCELLED, SUPERSEDED, STALE;

    public boolean terminal() {
        return this == COMPLETED || this == PARTIAL || this == UNEXECUTABLE || this == FAILED
                || this == CANCELLED || this == SUPERSEDED || this == STALE;
    }
}
