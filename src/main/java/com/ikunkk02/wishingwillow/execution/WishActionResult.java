package com.ikunkk02.wishingwillow.execution;

import com.ikunkk02.wishingwillow.execution.action.ActionResult;
import com.ikunkk02.wishingwillow.execution.action.ActionStatus;

public record WishActionResult(Status status, String code, int affected) {
    public enum Status { SUCCESS, PARTIAL_SUCCESS, RETRY, FAILED, TIMEOUT, UNSUPPORTED, STALE }

    public static WishActionResult success(int affected) { return new WishActionResult(Status.SUCCESS, "OK", affected); }
    public static WishActionResult partial(String code, int affected) { return new WishActionResult(Status.PARTIAL_SUCCESS, code, affected); }
    public static WishActionResult retry(String code) { return new WishActionResult(Status.RETRY, code, 0); }
    public static WishActionResult failed(String code) { return new WishActionResult(Status.FAILED, code, 0); }
    public static WishActionResult timeout(String code, int affected) { return new WishActionResult(Status.TIMEOUT, code, affected); }
    public static WishActionResult unsupported(String code) { return new WishActionResult(Status.UNSUPPORTED, code, 0); }
    public static WishActionResult stale(String code) { return new WishActionResult(Status.STALE, code, 0); }

    public boolean successful() { return status == Status.SUCCESS || status == Status.PARTIAL_SUCCESS; }

    public ActionResult toActionResult(int requested) {
        int total = Math.max(0, requested);
        int completed = Math.max(0, affected);
        ActionStatus publicStatus = switch (status) {
            case SUCCESS -> ActionStatus.SUCCESS;
            case PARTIAL_SUCCESS -> ActionStatus.PARTIAL;
            case TIMEOUT -> ActionStatus.TIMEOUT;
            case UNSUPPORTED -> ActionStatus.UNSUPPORTED;
            case RETRY, FAILED, STALE -> ActionStatus.FAILED;
        };
        return new ActionResult(publicStatus, total, completed, Math.max(0, total - completed), code);
    }
}
