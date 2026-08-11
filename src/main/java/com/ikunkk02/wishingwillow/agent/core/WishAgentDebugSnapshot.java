package com.ikunkk02.wishingwillow.agent.core;

import java.util.List;
import java.util.UUID;

public record WishAgentDebugSnapshot(
        UUID sessionId,
        WishPlanningMode mode,
        WishAgentDebugState state,
        int iterations,
        int toolCalls,
        List<String> toolsUsed,
        String lastTool,
        String lastToolStatus,
        WishVerificationState verificationState,
        WishFinalizationState finalizationState,
        WishAgentFallbackReason fallbackReason,
        long elapsedMs
) {
    public WishAgentDebugSnapshot {
        toolsUsed = List.copyOf(toolsUsed == null ? List.of() : toolsUsed);
        lastTool = lastTool == null ? "" : lastTool;
        lastToolStatus = lastToolStatus == null ? "" : lastToolStatus;
        fallbackReason = fallbackReason == null ? WishAgentFallbackReason.NONE : fallbackReason;
        elapsedMs = Math.max(0L, elapsedMs);
    }
}
