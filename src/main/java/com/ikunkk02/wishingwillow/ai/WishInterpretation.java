package com.ikunkk02.wishingwillow.ai;

import com.ikunkk02.wishingwillow.contract.WishContract;

import java.util.List;

public record WishInterpretation(
        int schemaVersion,
        String intent,
        String literalGoal,
        WishContract contract,
        WishFulfillment fulfillment,
        String reasoningSummary,
        WishTone tone,
        int severity,
        WishDelivery delivery,
        List<WishCapability> requiredCapabilities
) {
    public WishInterpretation {
        requiredCapabilities = List.copyOf(requiredCapabilities);
    }

    /** Source-compatible constructor for legacy tests and schema-v1 save migration. */
    public WishInterpretation(
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
        this(schemaVersion, intent, literalGoal, WishContract.legacy(literalGoal),
                new WishFulfillment(WishFulfillmentMode.CLASSIC, twistedOutcome,
                        List.of(FulfillmentStyle.IRONIC), Math.max(0, Math.min(100, severity))),
                reasoningSummary, tone, severity, delivery, requiredCapabilities);
    }

    /** Legacy debug/UI view; new schema-v2 code uses contract(). */
    public String loophole() {
        return contract.requiredOutcome();
    }

    /** Legacy debug/UI view; new schema-v2 code uses fulfillment().method(). */
    public String twistedOutcome() {
        return fulfillment.method();
    }
}
