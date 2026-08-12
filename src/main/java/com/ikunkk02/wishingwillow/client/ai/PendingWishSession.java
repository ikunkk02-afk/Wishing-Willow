package com.ikunkk02.wishingwillow.client.ai;

import com.ikunkk02.wishingwillow.ai.AiConfig;
import com.ikunkk02.wishingwillow.ai.WishInterpretationResult;
import com.ikunkk02.wishingwillow.wish.WishPipelineState;
import com.ikunkk02.wishingwillow.wish.WishSessionTerminationReason;

import javax.annotation.Nullable;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Thread-safe owner of one client wish pipeline and its asynchronous AI result. */
final class PendingWishSession {
    private final UUID sessionId;
    private final String wish;
    @Nullable private final AiConfig config;
    private final long generation;
    private final long createdAt;

    private WishPipelineState pipelineState = WishPipelineState.SUBMITTED;
    private WishCinematicState cinematicState = WishCinematicState.NOT_STARTED;
    @Nullable private CompletableFuture<WishInterpretationResult> aiFuture;
    @Nullable private WishInterpretationResult aiResult;
    private long lastUpdatedAt;
    private WishSessionTerminationReason terminalReason = WishSessionTerminationReason.NONE;

    PendingWishSession(UUID sessionId, String wish, @Nullable AiConfig config,
                       long generation, long createdAt) {
        this.sessionId = sessionId;
        this.wish = wish;
        this.config = config;
        this.generation = generation;
        this.createdAt = createdAt;
        this.lastUpdatedAt = createdAt;
    }

    synchronized void cinematicStarted(long now) {
        if (cinematicState == WishCinematicState.NOT_STARTED) {
            cinematicState = WishCinematicState.PLAYING;
            lastUpdatedAt = now;
        }
    }

    synchronized void cinematicFinished(long now) {
        if (cinematicState != WishCinematicState.CANCELLED) {
            cinematicState = WishCinematicState.FINISHED;
            lastUpdatedAt = now;
        }
    }

    synchronized boolean aiStarted(CompletableFuture<WishInterpretationResult> future, long now) {
        if (terminal() || aiFuture != null) return false;
        aiFuture = future;
        pipelineState = WishPipelineState.AI_PENDING;
        lastUpdatedAt = now;
        return true;
    }

    synchronized boolean aiCompleted(WishInterpretationResult result, long now) {
        if (terminal()) return false;
        aiResult = result;
        pipelineState = WishPipelineState.AI_RESULT_READY;
        lastUpdatedAt = now;
        return true;
    }

    @Nullable
    synchronized WishInterpretationResult takeAiResult() {
        WishInterpretationResult result = aiResult;
        aiResult = null;
        return result;
    }

    synchronized boolean transition(WishPipelineState next, long now) {
        if (terminal() || next == null || next.terminal()) return false;
        pipelineState = next;
        lastUpdatedAt = now;
        return true;
    }

    synchronized boolean terminate(WishSessionTerminationReason reason, long now) {
        if (terminal()) return false;
        terminalReason = reason == null ? WishSessionTerminationReason.STALE : reason;
        pipelineState = switch (terminalReason) {
            case EXECUTION_COMPLETE -> WishPipelineState.COMPLETED;
            case EXECUTION_PARTIAL -> WishPipelineState.PARTIAL_SUCCESS;
            case EXECUTION_UNEXECUTABLE -> WishPipelineState.UNEXECUTABLE;
            case USER_CANCELLED, PLAYER_DISCONNECT, WORLD_UNLOAD, SUPERSEDED -> WishPipelineState.CANCELLED;
            default -> WishPipelineState.FAILED;
        };
        if (terminalReason == WishSessionTerminationReason.USER_CANCELLED
                || terminalReason == WishSessionTerminationReason.SERVER_REJECTED) {
            cinematicState = WishCinematicState.CANCELLED;
        }
        lastUpdatedAt = now;
        if (aiFuture != null && !aiFuture.isDone()) aiFuture.cancel(true);
        aiResult = null;
        return true;
    }

    synchronized boolean terminal() { return pipelineState.terminal(); }
    synchronized boolean hintsShouldRemainActive() { return !pipelineState.terminal(); }
    synchronized WishPipelineState pipelineState() { return pipelineState; }
    synchronized WishCinematicState cinematicState() { return cinematicState; }
    synchronized long lastUpdatedAt() { return lastUpdatedAt; }
    synchronized WishSessionTerminationReason terminalReason() { return terminalReason; }
    synchronized boolean aiFutureDone() { return aiFuture != null && aiFuture.isDone(); }
    synchronized boolean aiFutureCancelled() { return aiFuture != null && aiFuture.isCancelled(); }

    UUID sessionId() { return sessionId; }
    String wish() { return wish; }
    @Nullable AiConfig config() { return config; }
    long generation() { return generation; }
    long createdAt() { return createdAt; }
}
