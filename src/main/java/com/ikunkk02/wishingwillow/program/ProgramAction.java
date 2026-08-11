package com.ikunkk02.wishingwillow.program;

import com.google.gson.JsonObject;
import com.ikunkk02.wishingwillow.ai.WishCapability;
import com.ikunkk02.wishingwillow.planning.CandidateReference;
import com.ikunkk02.wishingwillow.planning.WishTargetType;

import javax.annotation.Nullable;

/**
 * One flattened, executable leaf of a Wish Program after flow expansion
 * (sequence/parallel/repeat/delay are resolved into groups and delays).
 *
 * <p>This is the ONLY execution unit on the new WishProgram path. It is never derived from or
 * lowered into the legacy {@code WishPlanStep} model; the legacy executor adapts OLD data into
 * this shape (old-to-new), never the reverse.</p>
 */
public record ProgramAction(
        String actionId,
        JsonObject parameters,
        boolean presentation,
        int group,
        int delayTicks,
        WishTargetType target,
        @Nullable WishCapability capability,
        @Nullable CandidateReference candidate,
        int stepIndex
) {
    public ProgramAction {
        parameters = parameters == null ? new JsonObject() : parameters.deepCopy();
    }

    @Override
    public JsonObject parameters() { return parameters.deepCopy(); }

    public ProgramAction withCandidate(CandidateReference resolved) {
        return new ProgramAction(actionId, parameters, presentation, group, delayTicks,
                target, capability, resolved, stepIndex);
    }
}
