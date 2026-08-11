package com.ikunkk02.wishingwillow.client.music;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WishMusicStateMachineTest {
    @Test void wishSequenceContinuesTradeWithoutRestartAndTradeCannotInterruptWish() {
        WishMusicStateMachine state = new WishMusicStateMachine();
        assertEquals(WishMusicStateMachine.Action.START, state.start(WishMusicScene.TRADE_REVEAL));
        assertEquals(WishMusicStateMachine.Action.CONTINUE, state.start(WishMusicScene.WISH_SEQUENCE));
        assertEquals(WishMusicStateMachine.Action.NONE, state.start(WishMusicScene.TRADE_REVEAL));
        assertEquals(WishMusicState.WISH_SEQUENCE, state.state());
    }

    @Test void wishSequenceCanRecoverTheSameTrackAfterTradeFadeBegins() {
        WishMusicStateMachine state = new WishMusicStateMachine();
        state.start(WishMusicScene.TRADE_REVEAL);
        state.tradeScreenClosed();
        for (int tick = 0; tick <= 200; tick++) state.tick();
        assertEquals(WishMusicState.FADING_OUT, state.state());

        assertEquals(WishMusicStateMachine.Action.CONTINUE, state.start(WishMusicScene.WISH_SEQUENCE));
        assertEquals(WishMusicState.WISH_SEQUENCE, state.state());
        assertEquals(WishMusicStateMachine.Action.NONE, state.tick());
    }

    @Test void tradeCloseWaitsTenSecondsThenFadesForThreeSeconds() {
        WishMusicStateMachine state = new WishMusicStateMachine(); state.start(WishMusicScene.TRADE_REVEAL);
        state.tradeScreenClosed();
        for (int tick = 0; tick < 200; tick++) assertEquals(WishMusicStateMachine.Action.NONE, state.tick());
        assertEquals(WishMusicStateMachine.Action.BEGIN_FADE, state.tick());
        assertEquals(60, state.fadeTicks()); assertEquals(WishMusicState.FADING_OUT, state.state());
    }

    @Test void returningFromConfirmationDoesNotRestartAndOmenUsesOneSecondHoldFourSecondFade() {
        WishMusicStateMachine state = new WishMusicStateMachine(); state.start(WishMusicScene.WISH_SEQUENCE);
        assertEquals(WishMusicStateMachine.Action.NONE, state.start(WishMusicScene.WISH_SEQUENCE));
        state.omenFinished();
        for (int tick = 0; tick < 20; tick++) assertEquals(WishMusicStateMachine.Action.NONE, state.tick());
        assertEquals(WishMusicStateMachine.Action.BEGIN_FADE, state.tick()); assertEquals(80, state.fadeTicks());
    }

    @Test void cancellingWishStartsThreeSecondFadeWithoutRestart() {
        WishMusicStateMachine state = new WishMusicStateMachine(); state.start(WishMusicScene.WISH_SEQUENCE);
        state.wishCancelled();
        assertEquals(WishMusicStateMachine.Action.BEGIN_FADE, state.tick());
        assertEquals(60, state.fadeTicks());
        state.stopped(); assertEquals(WishMusicState.NONE, state.state());
    }
}
