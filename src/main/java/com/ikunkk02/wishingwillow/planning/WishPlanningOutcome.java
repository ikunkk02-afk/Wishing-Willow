package com.ikunkk02.wishingwillow.planning;

import com.ikunkk02.wishingwillow.agent.core.WishAgentDebugSnapshot;

public record WishPlanningOutcome(WishPlanResult result, CapabilityCatalog catalog,
                                  WishAgentDebugSnapshot debug) { }
