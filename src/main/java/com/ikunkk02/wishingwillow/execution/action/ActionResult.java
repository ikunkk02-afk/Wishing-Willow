package com.ikunkk02.wishingwillow.execution.action;

/** Deterministic evidence returned by an action; no AI review is involved. */
public record ActionResult(ActionStatus status, int requested, int completed, int failed, String message) {
    public ActionResult {
        if (requested < 0 || completed < 0 || failed < 0) throw new IllegalArgumentException("NEGATIVE_COUNT");
        message = message == null ? "" : message;
    }

    public static ActionResult success(int requested, int completed) {
        return new ActionResult(ActionStatus.SUCCESS, requested, completed,
                Math.max(0, requested - completed), "OK");
    }

    public static ActionResult timeout(int requested, int completed) {
        return new ActionResult(ActionStatus.TIMEOUT, requested, completed,
                Math.max(0, requested - completed), "ACTION_TIMEOUT");
    }
}
