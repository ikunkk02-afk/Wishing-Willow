package com.ikunkk02.wishingwillow.execution;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Deterministic pipeline probes used by integration tests to prove that a normal Wish Program
 * never touches the legacy WishPlan machinery. Counters are process-global and must be reset
 * with {@link #reset()} at the start of each test.
 *
 * <p>Expected for a plain direct program: legacyPlanCompileCount = 0, contractValidatorCount = 0,
 * contractReviewerCount = 0, agentRunCount = 0, while programExecutionCount and
 * actionExecutionCount grow.</p>
 */
public final class WishPipelineProbe {
    private static final AtomicLong LEGACY_PLAN_COMPILE = new AtomicLong();
    private static final AtomicLong CONTRACT_VALIDATOR = new AtomicLong();
    private static final AtomicLong CONTRACT_REVIEWER = new AtomicLong();
    private static final AtomicLong AGENT_RUN = new AtomicLong();
    private static final AtomicLong PROGRAM_EXECUTION = new AtomicLong();
    private static final AtomicLong ACTION_EXECUTION = new AtomicLong();
    private static final AtomicLong LEGACY_PLAN_START = new AtomicLong();

    private WishPipelineProbe() { }

    public static void legacyPlanCompile() { LEGACY_PLAN_COMPILE.incrementAndGet(); }
    public static void contractValidator() { CONTRACT_VALIDATOR.incrementAndGet(); }
    public static void contractReviewer() { CONTRACT_REVIEWER.incrementAndGet(); }
    public static void agentRun() { AGENT_RUN.incrementAndGet(); }
    public static void programExecution() { PROGRAM_EXECUTION.incrementAndGet(); }
    public static void actionExecution() { ACTION_EXECUTION.incrementAndGet(); }
    public static void legacyPlanStart() { LEGACY_PLAN_START.incrementAndGet(); }

    public static long legacyPlanCompileCount() { return LEGACY_PLAN_COMPILE.get(); }
    public static long contractValidatorCount() { return CONTRACT_VALIDATOR.get(); }
    public static long contractReviewerCount() { return CONTRACT_REVIEWER.get(); }
    public static long agentRunCount() { return AGENT_RUN.get(); }
    public static long programExecutionCount() { return PROGRAM_EXECUTION.get(); }
    public static long actionExecutionCount() { return ACTION_EXECUTION.get(); }
    public static long legacyPlanStartCount() { return LEGACY_PLAN_START.get(); }

    public static void reset() {
        LEGACY_PLAN_COMPILE.set(0); CONTRACT_VALIDATOR.set(0); CONTRACT_REVIEWER.set(0);
        AGENT_RUN.set(0); PROGRAM_EXECUTION.set(0); ACTION_EXECUTION.set(0); LEGACY_PLAN_START.set(0);
    }
}
