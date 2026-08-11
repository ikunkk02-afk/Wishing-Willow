package com.ikunkk02.wishingwillow.program;

import java.util.List;

/**
 * A WishProgram that passed server-side validation: strict schema, registry resource
 * resolution, safety policy and budget checks. Leaves carry canonical parameters and resolved
 * candidates and are executed natively — no legacy plan is ever created.
 */
public record ValidatedWishProgram(
        WishProgram program,
        List<ProgramAction> coreActions,
        List<ProgramAction> presentationActions
) {
    public List<ProgramAction> allLeaves() {
        return java.util.stream.Stream.concat(coreActions.stream(), presentationActions.stream()).toList();
    }

    public int leafCount() { return coreActions.size() + presentationActions.size(); }
}
