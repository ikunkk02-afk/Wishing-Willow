package com.ikunkk02.wishingwillow.wish;

/** Stable reason recorded whenever a client wish pipeline session is terminated. */
public enum WishSessionTerminationReason {
    NONE,
    EXECUTION_COMPLETE,
    EXECUTION_FAILED,
    AI_FAILED,
    WISH_REJECTED,
    PLANNING_FAILED,
    PROGRAM_REJECTED,
    SERVER_REJECTED,
    USER_CANCELLED,
    PLAYER_DISCONNECT,
    WORLD_UNLOAD,
    PIPELINE_TIMEOUT,
    SUPERSEDED,
    STALE
}
