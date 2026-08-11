package com.ikunkk02.wishingwillow.execution.action;

/** Deterministic evidence returned by an action; no AI review is involved. */
public record ActionResult(ActionStatus status, int requested, int completed, int failed, String code, String message) {
    public ActionResult {
        if (requested < 0 || completed < 0 || failed < 0) throw new IllegalArgumentException("NEGATIVE_COUNT");
        code = code == null ? "" : code;
        message = message == null ? "" : message;
    }

    public static ActionResult success(int requested, int completed) {
        return new ActionResult(ActionStatus.SUCCESS, requested, completed,
                Math.max(0, requested - completed), "OK", "OK");
    }

    public static ActionResult partial(int requested, int completed, String code) {
        return new ActionResult(ActionStatus.PARTIAL, requested, completed,
                Math.max(0, requested - completed), code, code);
    }

    public static ActionResult failed(int requested, int completed, String code) {
        return new ActionResult(ActionStatus.FAILED, requested, completed,
                Math.max(0, requested - completed), code, code);
    }

    public static ActionResult timeout(int requested, int completed) {
        return new ActionResult(ActionStatus.TIMEOUT, requested, completed,
                Math.max(0, requested - completed), "ACTION_TIMEOUT", "ACTION_TIMEOUT");
    }

    public static ActionResult cancelled(int requested, int completed) {
        return new ActionResult(ActionStatus.CANCELLED, requested, completed,
                Math.max(0, requested - completed), "CANCELLED", "CANCELLED");
    }

    public static ActionResult unsupported(String code) {
        return new ActionResult(ActionStatus.UNSUPPORTED, 0, 0, 0, code, code);
    }

    public static ActionResult stale(String code) {
        return new ActionResult(ActionStatus.STALE, 0, 0, 0, code, code);
    }

    public static ActionResult retry(String code) {
        return new ActionResult(ActionStatus.RETRY, 0, 0, 0, code, code);
    }
}
