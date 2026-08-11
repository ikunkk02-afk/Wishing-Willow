package com.ikunkk02.wishingwillow.planning.direct;

import com.ikunkk02.wishingwillow.planning.CapabilityCatalog;
import com.ikunkk02.wishingwillow.planning.WishPlanDraft;

import java.util.List;

public record CompiledDirectActionPlan(
        WishPlanDraft draft,
        CapabilityCatalog catalog,
        WishAbsurdityProfile absurdity,
        List<String> directActions,
        int droppedModifiers
) {
    public CompiledDirectActionPlan {
        directActions = List.copyOf(directActions);
    }
}
