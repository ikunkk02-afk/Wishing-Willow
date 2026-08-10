package com.ikunkk02.wishingwillow.ai;

import java.util.List;

public record WishInterpretation(
        int schemaVersion,
        String intent,
        String literalGoal,
        String loophole,
        String twistedOutcome,
        String reasoningSummary,
        WishTone tone,
        int severity,
        WishDelivery delivery,
        List<WishCapability> requiredCapabilities
) {
    public WishInterpretation {
        requiredCapabilities = List.copyOf(requiredCapabilities);
    }
}
