package com.ikunkk02.wishingwillow.execution;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WishActionResultTest {
    @Test
    void nextTickContinuationIsActionAgnostic() {
        WishActionResult result = WishActionResult.retryNextTick();
        assertEquals(WishActionResult.Status.RETRY, result.status());
        assertEquals("ACTION_BATCH_CONTINUE", result.code());
        assertTrue(result.shouldRetryNextTick());
        assertFalse(WishActionResult.retry("PLAYER_OFFLINE").shouldRetryNextTick());
    }
}