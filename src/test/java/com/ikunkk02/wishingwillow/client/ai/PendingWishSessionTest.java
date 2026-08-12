package com.ikunkk02.wishingwillow.client.ai;

import com.ikunkk02.wishingwillow.ai.AiErrorCategory;
import com.ikunkk02.wishingwillow.ai.WishInterpretationResult;
import com.ikunkk02.wishingwillow.wish.WishPipelineState;
import com.ikunkk02.wishingwillow.wish.WishSessionTerminationReason;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

class PendingWishSessionTest {
    @Test
    void cinematicFinishingBeforeDelayedAiDoesNotTerminateOrDropResult() {
        UUID sessionId = UUID.randomUUID();
        CompletableFuture<WishInterpretationResult> aiFuture = new CompletableFuture<>();
        PendingWishSession session = session(sessionId);

        session.cinematicStarted(1_000L);
        session.aiStarted(aiFuture, 1_010L);
        session.cinematicFinished(2_000L);

        assertEquals(WishCinematicState.FINISHED, session.cinematicState());
        assertEquals(WishPipelineState.AI_PENDING, session.pipelineState());
        assertFalse(session.terminal());
        assertTrue(session.hintsShouldRemainActive());

        WishInterpretationResult result = WishInterpretationResult.requestFailure(AiErrorCategory.UNKNOWN, 0);
        aiFuture.complete(result);
        assertTrue(session.aiCompleted(result, 11_000L));
        assertSame(result, session.takeAiResult());
        assertEquals(WishPipelineState.AI_RESULT_READY, session.pipelineState());
        assertFalse(session.terminal());
    }

    @Test
    void acceptedAiResultCanAdvanceIntoPlanningInsteadOfEndingAtNormalization() {
        PendingWishSession session = session(UUID.randomUUID());
        CompletableFuture<WishInterpretationResult> future = new CompletableFuture<>();
        session.aiStarted(future, 1_010L);
        WishInterpretationResult result = WishInterpretationResult.requestFailure(AiErrorCategory.UNKNOWN, 0);
        session.aiCompleted(result, 2_000L);

        assertTrue(session.transition(WishPipelineState.INTERPRETATION_SENT, 2_010L));
        assertTrue(session.transition(WishPipelineState.PLANNING, 2_020L));
        assertEquals(WishPipelineState.PLANNING, session.pipelineState());
        assertTrue(session.hintsShouldRemainActive());
    }

    @Test
    void registryRemovesOnlyTerminalPipelinesAndExecutionCompleteRecordsItsReason() {
        PendingWishSessionRegistry registry = new PendingWishSessionRegistry();
        PendingWishSession session = session(UUID.randomUUID());
        registry.register(session);
        session.cinematicFinished(2_000L);
        session.transition(WishPipelineState.PLANNING, 2_100L);

        assertTrue(registry.contains(session.sessionId()));
        PendingWishSession terminated = registry.terminate(session.sessionId(),
                WishSessionTerminationReason.EXECUTION_COMPLETE, 3_000L);

        assertSame(session, terminated);
        assertFalse(registry.contains(session.sessionId()));
        assertEquals(WishPipelineState.COMPLETED, session.pipelineState());
        assertEquals(WishSessionTerminationReason.EXECUTION_COMPLETE, session.terminalReason());
    }

    @Test
    void aiFailureIsTerminalAndCancelsOutstandingFuture() {
        PendingWishSessionRegistry registry = new PendingWishSessionRegistry();
        PendingWishSession session = session(UUID.randomUUID());
        CompletableFuture<WishInterpretationResult> future = new CompletableFuture<>();
        session.aiStarted(future, 1_010L);
        registry.register(session);

        registry.terminate(session.sessionId(), WishSessionTerminationReason.AI_FAILED, 2_000L);

        assertTrue(future.isCancelled());
        assertFalse(registry.contains(session.sessionId()));
        assertEquals(WishPipelineState.FAILED, session.pipelineState());
        assertEquals(WishSessionTerminationReason.AI_FAILED, session.terminalReason());
    }

    @Test
    void disconnectTerminatesEverySessionAndCancelsItsFuture() {
        PendingWishSessionRegistry registry = new PendingWishSessionRegistry();
        PendingWishSession first = session(UUID.randomUUID());
        PendingWishSession second = session(UUID.randomUUID());
        CompletableFuture<WishInterpretationResult> firstFuture = new CompletableFuture<>();
        CompletableFuture<WishInterpretationResult> secondFuture = new CompletableFuture<>();
        first.aiStarted(firstFuture, 1_010L);
        second.aiStarted(secondFuture, 1_020L);
        registry.register(first);
        registry.register(second);

        assertEquals(2, registry.terminateAll(WishSessionTerminationReason.PLAYER_DISCONNECT, 2_000L).size());

        assertEquals(0, registry.size());
        assertTrue(firstFuture.isCancelled());
        assertTrue(secondFuture.isCancelled());
        assertEquals(WishSessionTerminationReason.PLAYER_DISCONNECT, first.terminalReason());
        assertEquals(WishSessionTerminationReason.PLAYER_DISCONNECT, second.terminalReason());
    }

    private static PendingWishSession session(UUID sessionId) {
        return new PendingWishSession(sessionId, "我希望永远不孤单", null, 7L, 1_000L);
    }
}
