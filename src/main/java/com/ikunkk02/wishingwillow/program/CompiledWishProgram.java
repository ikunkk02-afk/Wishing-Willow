package com.ikunkk02.wishingwillow.program;

import java.util.List;

/**
 * Result of deterministically expanding a validated WishProgram into flat executable leaves.
 *
 * <p>Holds NO legacy plan artifacts ({@code WishPlanDraft}/{@code WishPlan}/{@code WishPlanStep}).
 * The native program executor consumes these leaves directly.</p>
 */
public record CompiledWishProgram(
        WishProgram program,
        List<ProgramAction> coreActions,
        List<ProgramAction> presentationActions,
        boolean skillUsed,
        boolean agentUsed
) {
    public List<ProgramAction> allLeaves() {
        return java.util.stream.Stream.concat(coreActions.stream(), presentationActions.stream()).toList();
    }

    public int leafCount() { return coreActions.size() + presentationActions.size(); }
}
