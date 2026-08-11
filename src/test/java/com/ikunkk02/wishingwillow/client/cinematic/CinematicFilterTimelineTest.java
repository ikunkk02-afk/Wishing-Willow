package com.ikunkk02.wishingwillow.client.cinematic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CinematicFilterTimelineTest {
    @Test void fadesInForEightTicksAndOutForThirtyTicks(){
        CinematicFilterTimeline timeline=new CinematicFilterTimeline();
        timeline.start();
        assertEquals(CinematicFilterState.FADING_IN,timeline.state());
        for(int tick=0;tick<7;tick++)timeline.tick();
        assertEquals(CinematicFilterState.FADING_IN,timeline.state());
        timeline.tick();
        assertEquals(CinematicFilterState.ACTIVE,timeline.state());
        assertEquals(1.0F,timeline.alpha(1.0F),0.0001F);

        timeline.finish();
        for(int tick=0;tick<29;tick++)timeline.tick();
        assertEquals(CinematicFilterState.FADING_OUT,timeline.state());
        timeline.tick();
        assertEquals(CinematicFilterState.OFF,timeline.state());
        assertEquals(0.0F,timeline.alpha(1.0F),0.0001F);
    }

    @Test void reopeningDuringFadeOutSmoothlyReversesFromCurrentAlpha(){
        CinematicFilterTimeline timeline=new CinematicFilterTimeline();
        timeline.start();
        for(int tick=0;tick<CinematicFilterTimeline.FADE_IN_TICKS;tick++)timeline.tick();
        timeline.finish();
        for(int tick=0;tick<15;tick++)timeline.tick();
        float before=timeline.alpha(1.0F);

        timeline.start();
        assertEquals(CinematicFilterState.FADING_IN,timeline.state());
        assertEquals(before,timeline.alpha(1.0F),0.0001F);
        timeline.tick();
        assertTrue(timeline.alpha(1.0F)>before);
        for(int tick=0;tick<CinematicFilterTimeline.FADE_IN_TICKS;tick++)timeline.tick();
        assertEquals(CinematicFilterState.ACTIVE,timeline.state());
    }

    @Test void clearAlwaysRemovesResidualOverlay(){
        CinematicFilterTimeline timeline=new CinematicFilterTimeline();
        timeline.start();
        timeline.tick();
        timeline.clear();
        assertEquals(CinematicFilterState.OFF,timeline.state());
        assertEquals(0.0F,timeline.alpha(1.0F),0.0001F);
    }
}
