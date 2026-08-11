package com.ikunkk02.wishingwillow.execution.action;

/** Stable result states exposed by the action-driven wish runtime. */
public enum ActionStatus {
    SUCCESS,
    PARTIAL,
    FAILED,
    TIMEOUT,
    CANCELLED,
    UNSUPPORTED,
    STALE,
    RETRY
}
