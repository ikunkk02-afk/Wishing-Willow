package com.ikunkk02.wishingwillow.omen;

import java.util.UUID;

public record WishOmen(
        UUID sessionId,
        WishOmenCategory category,
        String translationKey,
        int delayTicks
) {
    public WishOmen {
        if (translationKey == null || !translationKey.startsWith("omen.wishing_willow.")) {
            throw new IllegalArgumentException("Omen payload must be a local translation key");
        }
        delayTicks = Math.max(40, Math.min(100, delayTicks));
    }
}
