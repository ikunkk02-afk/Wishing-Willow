package com.ikunkk02.wishingwillow.wish;

import javax.annotation.Nullable;

public record WishResult(boolean accepted, WishRejectionReason reason, @Nullable WishSession session) {
    public static WishResult accepted(WishSession session) {
        return new WishResult(true, WishRejectionReason.NONE, session);
    }

    public static WishResult rejected(WishRejectionReason reason) {
        return new WishResult(false, reason, null);
    }
}
