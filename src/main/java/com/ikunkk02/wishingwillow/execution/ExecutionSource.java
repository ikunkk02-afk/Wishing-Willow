package com.ikunkk02.wishingwillow.execution;

/**
 * Distinguishes the two independent execution paths.
 *
 * <ul>
 *   <li>{@link #WISH_PROGRAM} — native WishProgram execution driven by
 *       {@code WishProgramExecutor}; never touches legacy plan artifacts.</li>
 *   <li>{@link #LEGACY_WISH_PLAN} — old saved WishPlan data executed by the legacy
 *       {@code WishExecutionManager} path for save compatibility only.</li>
 * </ul>
 */
public enum ExecutionSource {
    WISH_PROGRAM,
    LEGACY_WISH_PLAN
}
