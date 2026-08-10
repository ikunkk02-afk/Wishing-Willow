package com.ikunkk02.wishingwillow.unboxing;

import net.minecraft.world.InteractionHand;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class UnboxingSessionTest {
    @Test
    void followsAuthoritativeTimelineExactlyOnce() {
        UnboxingSession session = new UnboxingSession(
                UUID.randomUUID(), UUID.randomUUID(), InteractionHand.MAIN_HAND, 42L, 100L
        );
        assertTrue(session.transition(UnboxingState.UNOPENED, UnboxingState.UNBOXING));
        assertFalse(session.readyToRemove(139L));
        assertTrue(session.readyToRemove(140L));
        assertTrue(session.transition(UnboxingState.UNBOXING, UnboxingState.WILLOW_REMOVED));
        assertFalse(session.transition(UnboxingState.UNBOXING, UnboxingState.WILLOW_REMOVED));
        assertFalse(session.readyToFinish(165L));
        assertTrue(session.readyToFinish(166L));
        assertTrue(session.transition(UnboxingState.WILLOW_REMOVED, UnboxingState.FINISHED));
        assertTrue(session.state().terminal());
    }

    @Test
    void cancellationIsTerminalAndCannotBecomeRemoved() {
        UnboxingSession session = new UnboxingSession(
                UUID.randomUUID(), UUID.randomUUID(), InteractionHand.OFF_HAND, 7L, 0L
        );
        assertTrue(session.transition(UnboxingState.UNOPENED, UnboxingState.UNBOXING));
        assertTrue(session.transition(UnboxingState.UNBOXING, UnboxingState.CANCELLED));
        assertFalse(session.transition(UnboxingState.UNBOXING, UnboxingState.WILLOW_REMOVED));
        assertFalse(session.readyToRemove(1000L));
    }
}
