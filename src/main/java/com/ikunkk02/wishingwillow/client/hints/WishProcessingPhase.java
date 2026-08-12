package com.ikunkk02.wishingwillow.client.hints;

/**
 * Phases of wish processing visible to the action-bar hint system.
 * Ordered from earliest to latest; each phase has its own set of
 * translatable messages and controls when the hint loop stops.
 */
public enum WishProcessingPhase {
    /** Wish snapped; AI interpretation is running on the client. */
    INTERPRETING,
    /** AI interpretation succeeded; planning/agent is running. */
    PLANNING,
    /** Planning involves external research (CurseForge, GitHub, etc.). */
    RESEARCHING,
    /** Planning completed; server is validating and preparing execution. */
    PREPARING,
    /** Execution has started; hints remain until a terminal pipeline notification. */
    EXECUTING,
    /** Pipeline failed — show failure hint then stop. */
    FAILED
}
