package com.ikunkk02.wishingwillow.execution;

public record WishPolicyDecision(boolean allowed, WishExecutionAcceptError error, String detail) {
    public static WishPolicyDecision allow() {
        return new WishPolicyDecision(true, WishExecutionAcceptError.NONE, "");
    }

    public static WishPolicyDecision reject(WishExecutionAcceptError error, String detail) {
        return new WishPolicyDecision(false, error, detail == null ? "" : detail);
    }
}
