package com.ikunkk02.wishingwillow.execution;

import java.util.List;

/** Core actions determine success; optional presentation failures never overturn it. */
public final class WishProgramResultPolicy {
    private WishProgramResultPolicy() { }

    public static WishExecutionState reduce(List<WishStepExecutionState> core,
                                            List<WishStepExecutionState> presentation) {
        Summary summary = summarize(core, presentation);
        return switch (summary.outcome()) {
            case SUCCESS -> WishExecutionState.COMPLETED;
            case PARTIAL_SUCCESS -> WishExecutionState.PARTIAL;
            case UNEXECUTABLE -> WishExecutionState.UNEXECUTABLE;
            case FAILED -> WishExecutionState.FAILED;
        };
    }

    public static WishExecutionState reduceSteps(List<WishStepExecution> steps, int coreActionCount) {
        int coreCount = Math.min(Math.max(0, coreActionCount), steps.size());
        List<WishStepExecutionState> core = steps.subList(0, coreCount).stream()
                .map(WishStepExecution::state).toList();
        List<WishStepExecutionState> presentation = steps.subList(coreCount, steps.size()).stream()
                .map(WishStepExecution::state).toList();
        Summary summary = summarize(core, presentation);
        if (summary.outcome() != WishExecutionOutcome.UNEXECUTABLE || coreCount == 0) {
            return state(summary.outcome());
        }
        boolean internalFailure = steps.subList(0, coreCount).stream()
                .filter(step -> step.state() != WishStepExecutionState.SUCCEEDED)
                .map(WishStepExecution::lastError)
                .anyMatch(WishProgramResultPolicy::isInternalFailure);
        return internalFailure ? WishExecutionState.FAILED : WishExecutionState.UNEXECUTABLE;
    }

    public static Summary summarize(List<WishStepExecutionState> core,
                                    List<WishStepExecutionState> presentation) {
        int coreSuccess = count(core, WishStepExecutionState.SUCCEEDED);
        int coreFailed = core.size() - coreSuccess;
        int presentationSuccess = count(presentation, WishStepExecutionState.SUCCEEDED);
        int presentationFailed = presentation.size() - presentationSuccess;
        WishExecutionOutcome outcome;
        if (core.isEmpty()) outcome = WishExecutionOutcome.UNEXECUTABLE;
        else if (core.stream().anyMatch(state -> !state.terminal())) outcome = WishExecutionOutcome.FAILED;
        else if (coreSuccess > 0 && coreFailed == 0) outcome = WishExecutionOutcome.SUCCESS;
        else if (coreSuccess > 0) outcome = WishExecutionOutcome.PARTIAL_SUCCESS;
        else outcome = WishExecutionOutcome.UNEXECUTABLE;
        return new Summary(outcome, coreSuccess, coreFailed, presentationSuccess, presentationFailed);
    }

    public static WishExecutionOutcome outcomeForFailure(String code) {
        return isInternalFailure(code) ? WishExecutionOutcome.FAILED : WishExecutionOutcome.UNEXECUTABLE;
    }

    private static WishExecutionState state(WishExecutionOutcome outcome) {
        return switch (outcome) {
            case SUCCESS -> WishExecutionState.COMPLETED;
            case PARTIAL_SUCCESS -> WishExecutionState.PARTIAL;
            case UNEXECUTABLE -> WishExecutionState.UNEXECUTABLE;
            case FAILED -> WishExecutionState.FAILED;
        };
    }

    private static boolean isInternalFailure(String code) {
        return code != null && (code.startsWith("ACTION_EXCEPTION_")
                || code.equals("PROGRAM_TIMEOUT") || code.equals("SKILL_TIMEOUT")
                || code.equals("LOOP_DETECTED") || code.equals("PROGRAM_MISSING")
                || code.startsWith("PROGRAM_REVALIDATION_FAILED"));
    }

    private static int count(List<WishStepExecutionState> states, WishStepExecutionState wanted) {
        return (int) states.stream().filter(wanted::equals).count();
    }

    public record Summary(WishExecutionOutcome outcome, int coreSuccess, int coreFailed,
                          int presentationSuccess, int presentationFailed) { }
}
