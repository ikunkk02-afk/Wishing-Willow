package com.ikunkk02.wishingwillow.ai;

import java.util.List;
import java.util.Locale;

/** Rejects refusal prose before any AI-authored text can become player-visible. */
public final class WishRefusalGuard {
    private static final List<String> FORBIDDEN = List.of(
            "cannot", "can't", "unable", "unsafe", "impossible", "no safe",
            "refuse", "无法", "不能", "不可实现", "没有安全", "拒绝实现"
    );

    private WishRefusalGuard() {
    }

    public static boolean containsRefusal(String value) {
        if (value == null || value.isBlank()) return false;
        String normalized = value.toLowerCase(Locale.ROOT);
        return FORBIDDEN.stream().anyMatch(normalized::contains);
    }

    public static void requireAllowed(String... values) {
        for (String value : values) {
            if (containsRefusal(value)) throw new IllegalArgumentException("REFUSAL_RESPONSE");
        }
    }
}
