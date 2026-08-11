package com.ikunkk02.wishingwillow.agent.core;

import com.ikunkk02.wishingwillow.planning.CapabilityCatalog;
import com.ikunkk02.wishingwillow.planning.WishPlanResult;

public record WishAgentRunResult(WishPlanResult result, CapabilityCatalog catalog,
                                 WishAgentDebugSnapshot debug) { }
