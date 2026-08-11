package com.ikunkk02.wishingwillow.agent.core;

public enum WishAgentDebugState {
    AGENT_STARTED,
    ITERATION_STARTED,
    TOOL_CALLED,
    TOOL_RESULT,
    FALLBACK_STARTED,
    COMPATIBILITY_STARTED,
    VALIDATION,
    COMPLETED,
    FAILED,
    CANCELLED
}
