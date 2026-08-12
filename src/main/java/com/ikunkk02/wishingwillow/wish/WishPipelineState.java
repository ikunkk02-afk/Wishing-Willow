package com.ikunkk02.wishingwillow.wish;

/** Business lifecycle of a wish, independent from every cinematic or UI animation. */
public enum WishPipelineState {
    SUBMITTED,
    AI_PENDING,
    AI_RESULT_READY,
    INTERPRETATION_SENT,
    PLANNING,
    RESEARCHING,
    PROGRAM_READY,
    PROGRAM_SENT,
    EXECUTING,
    COMPLETED,
    FAILED,
    CANCELLED;

    public boolean terminal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED;
    }
}
