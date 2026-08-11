package com.ikunkk02.wishingwillow.planning;

public record WishRouteDecision(WishExecutionRoute route, String reason) {
    public WishRouteDecision {
        reason = reason == null ? "" : reason.strip();
    }
}
