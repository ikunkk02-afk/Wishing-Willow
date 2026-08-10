package com.ikunkk02.wishingwillow.planning;

import com.google.gson.JsonObject;
import com.ikunkk02.wishingwillow.ai.WishCapability;

public record WishPlanStep(
        int stepIndex,
        WishStepTiming timing,
        int delaySeconds,
        WishTriggerType trigger,
        WishActionType action,
        WishCapability capability,
        String candidateId,
        WishTargetType target,
        JsonObject parameters,
        String selectionReason,
        CandidateReference candidateReference
) {
    public WishPlanStep {
        parameters = parameters.deepCopy();
    }
}
