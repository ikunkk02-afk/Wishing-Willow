package com.ikunkk02.wishingwillow.advancement;

import com.ikunkk02.wishingwillow.ai.WishTone;

public record WishOutcomeSummary(
        int successfulActionCount,
        boolean absurd,
        boolean persistent,
        boolean negative,
        WishSeverity severity,
        WishTone tone
) {
    public WishOutcomeSummary {
        successfulActionCount = Math.max(0, successfulActionCount);
        severity = severity == null ? WishSeverity.NORMAL : severity;
        tone = tone == null ? WishTone.NEUTRAL : tone;
    }

    public static WishOutcomeSummary normal(int successfulActionCount) {
        return new WishOutcomeSummary(successfulActionCount, false, false, false,
                WishSeverity.NORMAL, WishTone.NEUTRAL);
    }

    public boolean dangerous() {
        return severity == WishSeverity.DANGEROUS || severity == WishSeverity.CATASTROPHIC;
    }
}
