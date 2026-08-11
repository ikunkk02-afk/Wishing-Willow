package com.ikunkk02.wishingwillow.planning.direct;

import java.util.List;
import java.util.Objects;

public record WishAbsurdityProfile(
        WishAbsurdityStyle style,
        int intensity,
        List<DirectWishAction> modifiers
) {
    public WishAbsurdityProfile {
        style = Objects.requireNonNull(style);
        modifiers = List.copyOf(modifiers == null ? List.of() : modifiers);
        if (intensity < 0 || intensity > 100 || modifiers.size() > 3) {
            throw new IllegalArgumentException("INVALID_ABSURDITY_PROFILE");
        }
    }

    public static WishAbsurdityProfile none() {
        return new WishAbsurdityProfile(WishAbsurdityStyle.NONE, 0, List.of());
    }
}
