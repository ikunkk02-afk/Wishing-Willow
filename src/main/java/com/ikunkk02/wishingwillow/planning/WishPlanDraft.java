package com.ikunkk02.wishingwillow.planning;

import com.ikunkk02.wishingwillow.ai.WishDelivery;

import java.util.List;

public record WishPlanDraft(int schemaVersion, String summary, WishDelivery delivery, int severity,
                            WishEstimatedDuration estimatedDuration, List<WishPlanStep> steps) {
    public WishPlanDraft { steps = List.copyOf(steps); }
}
