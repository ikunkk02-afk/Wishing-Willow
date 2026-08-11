package com.ikunkk02.wishingwillow.agent.core;

import com.ikunkk02.wishingwillow.planning.WishExecutionRoute;
import com.ikunkk02.wishingwillow.planning.direct.WishAbsurdityStyle;

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
        long elapsedMs,
        WishExecutionRoute route,
        String routeReason,
        String coreOutcome,
        WishAbsurdityStyle absurdityStyle,
        int absurdityIntensity,
        List<String> directActions
) {
    public WishAgentDebugSnapshot {
        toolsUsed = List.copyOf(toolsUsed == null ? List.of() : toolsUsed);
        lastTool = lastTool == null ? "" : lastTool;
        lastToolStatus = lastToolStatus == null ? "" : lastToolStatus;
        fallbackReason = fallbackReason == null ? WishAgentFallbackReason.NONE : fallbackReason;
        elapsedMs = Math.max(0L, elapsedMs);
        route = route == null ? (mode == WishPlanningMode.DIRECT_ACTION_MODE
                ? WishExecutionRoute.DIRECT_ACTION : WishExecutionRoute.COMPLEX_AGENT) : route;
        routeReason = clean(routeReason, 160);
        coreOutcome = clean(coreOutcome, 512);
        absurdityStyle = absurdityStyle == null ? WishAbsurdityStyle.NONE : absurdityStyle;
        absurdityIntensity = Math.max(0, Math.min(100, absurdityIntensity));
        directActions = List.copyOf(directActions == null ? List.of() : directActions);
    }

    public WishAgentDebugSnapshot(UUID sessionId, WishPlanningMode mode, WishAgentDebugState state,
                                  int iterations, int toolCalls, List<String> toolsUsed,
                                  String lastTool, String lastToolStatus,
                                  WishVerificationState verificationState,
                                  WishFinalizationState finalizationState,
                                  WishAgentFallbackReason fallbackReason, long elapsedMs) {
        this(sessionId, mode, state, iterations, toolCalls, toolsUsed, lastTool, lastToolStatus,
                verificationState, finalizationState, fallbackReason, elapsedMs,
                mode == WishPlanningMode.DIRECT_ACTION_MODE
                        ? WishExecutionRoute.DIRECT_ACTION : WishExecutionRoute.COMPLEX_AGENT,
                "", "", WishAbsurdityStyle.NONE, 0, List.of());
    }

    private static String clean(String value, int max) {
        String clean = value == null ? "" : value.replace('\n', ' ').replace('\r', ' ').strip();
        return clean.length() <= max ? clean : clean.substring(0, max);
    }
}
