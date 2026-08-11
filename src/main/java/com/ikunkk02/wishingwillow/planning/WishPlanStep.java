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
        CandidateReference candidateReference,
        String batchId
) {
    public WishPlanStep {
        parameters = parameters.deepCopy();
        batchId = batchId == null ? "" : batchId.strip();
        if (batchId.length() > 64) throw new IllegalArgumentException("INVALID_BATCH_ID");
    }

    public WishPlanStep(int stepIndex, WishStepTiming timing, int delaySeconds, WishTriggerType trigger,
                        WishActionType action, WishCapability capability, String candidateId,
                        WishTargetType target, JsonObject parameters, String selectionReason,
                        CandidateReference candidateReference) {
        this(stepIndex, timing, delaySeconds, trigger, action, capability, candidateId, target,
                parameters, selectionReason, candidateReference, "");
    }
}
