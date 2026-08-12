package com.ikunkk02.wishingwillow.program;

import javax.annotation.Nullable;
import java.util.List;

/** Result of the tolerant AI boundary; only ACCEPT/REPAIRABLE carry a program. */
public record WishProgramNormalizationResult(
        WishProgramValidationStatus status,
        @Nullable WishProgram program,
        List<WishNormalizationChange> changes,
        int droppedActions,
        @Nullable WishProgramValidationIssue issue
) {
    public WishProgramNormalizationResult {
        changes = List.copyOf(changes == null ? List.of() : changes);
        if (droppedActions < 0) throw new IllegalArgumentException("NEGATIVE_DROPPED_ACTIONS");
        if (status == WishProgramValidationStatus.REJECT && program != null) {
            throw new IllegalArgumentException("REJECT_MUST_NOT_HAVE_PROGRAM");
        }
        if (status != WishProgramValidationStatus.REJECT && program == null) {
            throw new IllegalArgumentException("ACCEPTED_NORMALIZATION_REQUIRES_PROGRAM");
        }
    }

    public int repairCount() {
        return changes.size();
    }

    public WishProgram requireProgram() {
        if (program != null) return program;
        throw new WishProgramNormalizationException(issue == null
                ? new WishProgramValidationIssue("INVALID_WISH_PROGRAM:UNKNOWN", "", "",
                null, null, null, false, "") : issue);
    }
}
