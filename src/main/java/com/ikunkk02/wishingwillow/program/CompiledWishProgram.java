package com.ikunkk02.wishingwillow.program;

import com.ikunkk02.wishingwillow.planning.CapabilityCatalog;
import com.ikunkk02.wishingwillow.planning.WishPlanDraft;

import java.util.List;

public record CompiledWishProgram(
        WishProgram program,
        WishPlanDraft draft,
        CapabilityCatalog catalog,
        List<String> coreActions,
        List<String> presentationActions,
        boolean skillUsed,
        boolean agentUsed
) {
    public CompiledWishProgram {
        coreActions = List.copyOf(coreActions);
        presentationActions = List.copyOf(presentationActions);
    }
}
