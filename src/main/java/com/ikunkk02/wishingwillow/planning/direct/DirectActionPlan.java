package com.ikunkk02.wishingwillow.planning.direct;

import com.ikunkk02.wishingwillow.planning.WishExecutionRoute;

import java.util.List;
import java.util.Objects;

public record DirectActionPlan(
        WishExecutionRoute route,
        String summary,
        List<DirectWishAction> actions,
        WishAbsurdityProfile absurdity
) {
    public DirectActionPlan {
        route = Objects.requireNonNull(route);
        summary = Objects.requireNonNullElse(summary, "").strip();
        actions = List.copyOf(actions == null ? List.of() : actions);
        absurdity = absurdity == null ? WishAbsurdityProfile.none() : absurdity;
        if (summary.isEmpty() || summary.length() > 512 || actions.size() > 32) {
            throw new IllegalArgumentException("INVALID_DIRECT_ACTION_PLAN");
        }
        if (route == WishExecutionRoute.DIRECT_ACTION && actions.isEmpty()) {
            throw new IllegalArgumentException("EMPTY_DIRECT_ACTION_PLAN");
        }
        if (route == WishExecutionRoute.COMPLEX_AGENT && !actions.isEmpty()) {
            throw new IllegalArgumentException("COMPLEX_ROUTE_HAS_DIRECT_ACTIONS");
        }
    }
}
