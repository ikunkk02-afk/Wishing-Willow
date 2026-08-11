package com.ikunkk02.wishingwillow.agent.core;

import java.util.List;
import java.util.UUID;

public record WishAgentDebugSnapshot(
        UUID sessionId,
        WishPlanningMode mode,
        int iterations,
        int toolCalls,
        List<String> toolsUsed,
        WishVerificationState verificationState,
        WishFinalizationState finalizationState
) {
    public WishAgentDebugSnapshot {
        toolsUsed = List.copyOf(toolsUsed == null ? List.of() : toolsUsed);
    }
}
