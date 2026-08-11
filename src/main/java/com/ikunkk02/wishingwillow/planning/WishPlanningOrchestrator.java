package com.ikunkk02.wishingwillow.planning;

import com.ikunkk02.wishingwillow.agent.core.*;
import com.ikunkk02.wishingwillow.ai.AiRequestException;
import com.ikunkk02.wishingwillow.ai.ToolCallingSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Explicit three-state routing and fail-open-to-JSON reliability boundary for wish planning. */
public final class WishPlanningOrchestrator {
    public static final Duration DEFAULT_PROBE_TIMEOUT = Duration.ofSeconds(8);
    public static final Duration DEFAULT_AGENT_TIMEOUT = WishAgentSession.MAX_DURATION.plusSeconds(1);
    public static final Duration DEFAULT_COMPATIBILITY_TIMEOUT = Duration.ofSeconds(75);

    private static final Logger LOGGER = LoggerFactory.getLogger(WishPlanningOrchestrator.class);
    private final Duration probeTimeout;
    private final Duration agentTimeout;
    private final Duration compatibilityTimeout;

    public WishPlanningOrchestrator() {
        this(DEFAULT_PROBE_TIMEOUT, DEFAULT_AGENT_TIMEOUT, DEFAULT_COMPATIBILITY_TIMEOUT);
    }

    public WishPlanningOrchestrator(Duration probeTimeout, Duration agentTimeout, Duration compatibilityTimeout) {
        this.probeTimeout = positive(probeTimeout);
        this.agentTimeout = positive(agentTimeout);
        this.compatibilityTimeout = positive(compatibilityTimeout);
    }

    public CompletableFuture<WishPlanningOutcome> plan(
            UUID sessionId,
            ToolCallingSupport initialSupport,
            CapabilityCatalog initialCatalog,
            Supplier<CompletableFuture<ToolCallingSupport>> probe,
            Supplier<CompletableFuture<WishAgentRunResult>> agent,
            Supplier<CompletableFuture<WishPlanResult>> compatibility,
            BooleanSupplier cancelled,
            Consumer<WishAgentDebugSnapshot> debugListener
    ) {
        BooleanSupplier cancellation = cancelled == null ? () -> false : cancelled;
        Consumer<WishAgentDebugSnapshot> debug = debugListener == null ? ignored -> { } : debugListener;
        if (cancellation.getAsBoolean()) return CompletableFuture.completedFuture(cancelled(sessionId));
        CompletableFuture<ToolCallingSupport> support;
        if (initialSupport == ToolCallingSupport.UNKNOWN) {
            support = safe(probe).completeOnTimeout(ToolCallingSupport.UNKNOWN,
                    probeTimeout.toMillis(), TimeUnit.MILLISECONDS)
                    .exceptionally(error -> ToolCallingSupport.UNKNOWN);
        } else {
            support = CompletableFuture.completedFuture(initialSupport);
        }
        return support.thenCompose(value -> {
            if (cancellation.getAsBoolean()) return CompletableFuture.completedFuture(cancelled(sessionId));
            if (value != ToolCallingSupport.SUPPORTED) {
                WishAgentFallbackReason reason = value == ToolCallingSupport.UNSUPPORTED
                        ? WishAgentFallbackReason.TOOL_CALLING_UNSUPPORTED
                        : WishAgentFallbackReason.TOOL_SUPPORT_PROBE_FAILED;
                return compatibility(sessionId, initialCatalog, null, reason, compatibility, cancellation, debug);
            }
            CompletableFuture<WishAgentRunResult> attempt = safe(agent)
                    .orTimeout(agentTimeout.toMillis(), TimeUnit.MILLISECONDS);
            return attempt.handle((result, error) -> new AgentAttempt(result, error)).thenCompose(completed -> {
                if (cancellation.getAsBoolean()) return CompletableFuture.completedFuture(cancelled(sessionId));
                if (completed.error == null && completed.result != null
                        && completed.result.result().draft() != null) {
                    return CompletableFuture.completedFuture(new WishPlanningOutcome(completed.result.result(),
                            completed.result.catalog(), completed.result.debug()));
                }
                WishAgentDebugSnapshot prior = completed.result == null ? null : completed.result.debug();
                WishAgentFallbackReason reason = prior != null && prior.fallbackReason() != WishAgentFallbackReason.NONE
                        ? prior.fallbackReason() : classify(completed.error);
                return compatibility(sessionId, initialCatalog, prior, reason, compatibility, cancellation, debug);
            });
        });
    }

    private CompletableFuture<WishPlanningOutcome> compatibility(
            UUID sessionId, CapabilityCatalog catalog, WishAgentDebugSnapshot prior,
            WishAgentFallbackReason reason, Supplier<CompletableFuture<WishPlanResult>> planner,
            BooleanSupplier cancelled, Consumer<WishAgentDebugSnapshot> debug
    ) {
        WishAgentDebugSnapshot fallback = snapshot(sessionId, prior, WishAgentDebugState.FALLBACK_STARTED,
                WishPlanningMode.COMPATIBILITY_JSON_MODE, WishFinalizationState.NOT_ATTEMPTED, reason);
        debug.accept(fallback);
        WishAgentDebugSnapshot started = snapshot(sessionId, prior, WishAgentDebugState.COMPATIBILITY_STARTED,
                WishPlanningMode.COMPATIBILITY_JSON_MODE, WishFinalizationState.NOT_ATTEMPTED, reason);
        debug.accept(started);
        LOGGER.info("Wish agent fallback session={} reason={} iterations={} toolCalls={} elapsedMs={}", sessionId,
                reason, started.iterations(), started.toolCalls(), started.elapsedMs());
        LOGGER.info("Compatibility planner started session={} reason={}", sessionId, reason);
        long began = System.nanoTime();
        return safe(planner).orTimeout(compatibilityTimeout.toMillis(), TimeUnit.MILLISECONDS)
                .handle((result, error) -> {
                    if (cancelled.getAsBoolean()) return cancelled(sessionId);
                    WishPlanResult accepted = result;
                    if (error != null || result == null) {
                        accepted = WishPlanResult.failed(root(error) instanceof TimeoutException
                                ? WishPlanError.AI_TIMEOUT : WishPlanError.AI_REQUEST_FAILED);
                    }
                    if (accepted.draft() != null) {
                        debug.accept(snapshot(sessionId, started, WishAgentDebugState.VALIDATION,
                                WishPlanningMode.COMPATIBILITY_JSON_MODE, WishFinalizationState.SUCCESS, reason));
                    }
                    WishAgentDebugState finalState = accepted.draft() == null
                            ? WishAgentDebugState.FAILED : WishAgentDebugState.COMPLETED;
                    WishFinalizationState finalization = accepted.draft() == null
                            ? (contractFailure(accepted.error()) ? WishFinalizationState.REJECTED
                            : WishFinalizationState.TECHNICAL_FAILURE) : WishFinalizationState.SUCCESS;
                    long elapsed = started.elapsedMs()
                            + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - began);
                    WishAgentDebugSnapshot finished = new WishAgentDebugSnapshot(sessionId,
                            WishPlanningMode.COMPATIBILITY_JSON_MODE, finalState, started.iterations(),
                            started.toolCalls(), started.toolsUsed(), started.lastTool(), started.lastToolStatus(),
                            started.verificationState(), finalization, reason, elapsed,
                            started.route(), started.routeReason(), started.coreOutcome(),
                            started.absurdityStyle(), started.absurdityIntensity(), started.directActions());
                    debug.accept(finished);
                    LOGGER.info("Compatibility planner completed session={} state={} error={} steps={} elapsedMs={}",
                            sessionId, accepted.state(), accepted.error(),
                            accepted.draft() == null ? 0 : accepted.draft().steps().size(),
                            TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - began));
                    return new WishPlanningOutcome(accepted, catalog, finished);
                });
    }

    private static WishAgentDebugSnapshot snapshot(UUID sessionId, WishAgentDebugSnapshot prior,
                                                    WishAgentDebugState state, WishPlanningMode mode,
                                                    WishFinalizationState finalization,
                                                    WishAgentFallbackReason reason) {
        return new WishAgentDebugSnapshot(sessionId, mode, state,
                prior == null ? 0 : prior.iterations(), prior == null ? 0 : prior.toolCalls(),
                prior == null ? List.of() : prior.toolsUsed(), prior == null ? "" : prior.lastTool(),
                prior == null ? "" : prior.lastToolStatus(),
                prior == null ? WishVerificationState.NOT_VERIFIED : prior.verificationState(),
                finalization, reason, prior == null ? 0L : prior.elapsedMs(),
                WishExecutionRoute.COMPLEX_AGENT,
                prior == null ? "complex_agent_route" : prior.routeReason(),
                prior == null ? "" : prior.coreOutcome(),
                prior == null ? com.ikunkk02.wishingwillow.planning.direct.WishAbsurdityStyle.NONE : prior.absurdityStyle(),
                prior == null ? 0 : prior.absurdityIntensity(), List.of());
    }

    private static WishPlanningOutcome cancelled(UUID sessionId) {
        WishAgentDebugSnapshot debug = new WishAgentDebugSnapshot(sessionId, WishPlanningMode.AGENT_TOOL_MODE,
                WishAgentDebugState.CANCELLED, 0, 0, List.of(), "", "",
                WishVerificationState.NOT_VERIFIED, WishFinalizationState.CANCELLED,
                WishAgentFallbackReason.CANCELLED, 0L);
        return new WishPlanningOutcome(WishPlanResult.failed(WishPlanError.AI_REQUEST_FAILED), null, debug);
    }

    private static WishAgentFallbackReason classify(Throwable error) {
        Throwable cause = root(error);
        if (cause instanceof TimeoutException) return WishAgentFallbackReason.AGENT_DEADLINE_EXCEEDED;
        if (cause instanceof AiRequestException request) {
            return switch (request.category()) {
                case TIMEOUT -> WishAgentFallbackReason.AI_REQUEST_TIMEOUT;
                case MALFORMED_RESPONSE -> WishAgentFallbackReason.MALFORMED_RESPONSE;
                case EMPTY_RESPONSE -> WishAgentFallbackReason.EMPTY_RESPONSE;
                case UNSUPPORTED_FEATURE -> WishAgentFallbackReason.TOOL_CALLING_UNSUPPORTED;
                default -> WishAgentFallbackReason.AGENT_TECHNICAL_FAILURE;
            };
        }
        return WishAgentFallbackReason.AGENT_TECHNICAL_FAILURE;
    }

    private static boolean contractFailure(WishPlanError error) {
        return error == WishPlanError.NO_CANDIDATES
                || error == WishPlanError.UNSATISFIED_CAPABILITIES
                || error == WishPlanError.CONTRACT_NOT_FULFILLED;
    }

    private static Throwable root(Throwable error) {
        if (error == null) return null;
        Throwable current = error;
        while ((current instanceof CompletionException || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) current = current.getCause();
        return current;
    }

    private static <T> CompletableFuture<T> safe(Supplier<CompletableFuture<T>> supplier) {
        try {
            CompletableFuture<T> future = supplier.get();
            return future == null ? CompletableFuture.failedFuture(new IllegalStateException("NULL_FUTURE")) : future;
        } catch (RuntimeException exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    private static Duration positive(Duration duration) {
        if (duration == null || duration.isZero() || duration.isNegative()) throw new IllegalArgumentException("INVALID_TIMEOUT");
        return duration;
    }

    private record AgentAttempt(WishAgentRunResult result, Throwable error) { }
}
