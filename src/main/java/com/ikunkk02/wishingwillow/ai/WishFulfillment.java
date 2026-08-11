package com.ikunkk02.wishingwillow.ai;

import java.util.List;
import java.util.Objects;

public record WishFulfillment(
        WishFulfillmentMode mode,
        String method,
        List<FulfillmentStyle> styles,
        int absurdity
) {
    public WishFulfillment {
        mode = Objects.requireNonNull(mode);
        method = Objects.requireNonNullElse(method, "").strip();
        styles = List.copyOf(styles == null ? List.of() : styles);
        if (method.isEmpty() || method.length() > 1024 || styles.isEmpty() || styles.size() > 3
                || styles.stream().distinct().count() != styles.size() || absurdity < 0 || absurdity > 100) {
            throw new IllegalArgumentException("INVALID_WISH_FULFILLMENT");
        }
    }
}
