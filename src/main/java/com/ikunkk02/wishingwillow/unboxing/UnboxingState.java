package com.ikunkk02.wishingwillow.unboxing;

public enum UnboxingState {
    UNOPENED,
    UNBOXING,
    WILLOW_REMOVED,
    FINISHED,
    CANCELLED;

    public boolean terminal() {
        return this == FINISHED || this == CANCELLED;
    }
}
