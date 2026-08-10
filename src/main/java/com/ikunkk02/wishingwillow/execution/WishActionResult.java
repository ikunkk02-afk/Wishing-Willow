package com.ikunkk02.wishingwillow.execution;

public record WishActionResult(Status status, String code, int affected) {
    public enum Status { SUCCESS, PARTIAL_SUCCESS, RETRY, FAILED, UNSUPPORTED, STALE }

    public static WishActionResult success(int affected) { return new WishActionResult(Status.SUCCESS, "OK", affected); }
    public static WishActionResult partial(String code, int affected) { return new WishActionResult(Status.PARTIAL_SUCCESS, code, affected); }
    public static WishActionResult retry(String code) { return new WishActionResult(Status.RETRY, code, 0); }
    public static WishActionResult failed(String code) { return new WishActionResult(Status.FAILED, code, 0); }
    public static WishActionResult unsupported(String code) { return new WishActionResult(Status.UNSUPPORTED, code, 0); }
    public static WishActionResult stale(String code) { return new WishActionResult(Status.STALE, code, 0); }

    public boolean successful() { return status == Status.SUCCESS || status == Status.PARTIAL_SUCCESS; }
}
