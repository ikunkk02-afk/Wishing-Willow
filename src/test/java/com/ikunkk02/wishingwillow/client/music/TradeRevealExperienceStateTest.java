package com.ikunkk02.wishingwillow.client.music;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TradeRevealExperienceStateTest {
    @Test void ordinaryMerchantNeverTriggersTheReveal(){
        TradeRevealExperienceState state=new TradeRevealExperienceState();
        assertEquals(TradeRevealExperienceState.Update.NONE,state.observe(true,false));
        assertEquals(TradeRevealExperienceState.Update.NONE,state.observe(true,false));
        assertEquals(TradeRevealExperienceState.Update.NONE,state.observe(false,false));
    }

    @Test void willowOfferTriggersOnceAndPurchaseKeepsTheExperienceActive(){
        TradeRevealExperienceState state=new TradeRevealExperienceState();
        state.observe(true,false);
        assertEquals(TradeRevealExperienceState.Update.REVEALED,state.observe(true,true));
        assertFalse(state.shouldScanOffers());
        assertEquals(TradeRevealExperienceState.Update.NONE,state.observe(true,false));
        assertTrue(state.revealActive());
        assertEquals(TradeRevealExperienceState.Update.CLOSED_REVEAL,state.observe(false,false));
    }

    @Test void reopeningTheSameWillowTradeCanRevealAgain(){
        TradeRevealExperienceState state=new TradeRevealExperienceState();
        state.observe(true,false);
        assertEquals(TradeRevealExperienceState.Update.REVEALED,state.observe(true,true));
        assertEquals(TradeRevealExperienceState.Update.CLOSED_REVEAL,state.observe(false,false));
        state.observe(true,false);
        assertEquals(TradeRevealExperienceState.Update.REVEALED,state.observe(true,true));
    }
}
