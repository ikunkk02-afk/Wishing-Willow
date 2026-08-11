package com.ikunkk02.wishingwillow.client.cinematic;

final class CinematicFilterTimeline {
    static final int FADE_IN_TICKS = 8;
    static final int FADE_OUT_TICKS = 30;
    private CinematicFilterState state = CinematicFilterState.OFF;
    private float alpha;
    private float previousAlpha;
    private float transitionStart;
    private int transitionTick;
    private int transitionDuration;

    CinematicFilterState state() {
        return state;
    }

    float alpha(float partialTick) {
        float partial = Math.max(0.0F, Math.min(1.0F, partialTick));
        return previousAlpha + (alpha - previousAlpha) * partial;
    }

    void start() {
        if (state == CinematicFilterState.ACTIVE || state == CinematicFilterState.FADING_IN) return;
        begin(CinematicFilterState.FADING_IN, Math.max(1,
                Math.round(FADE_IN_TICKS * (1.0F - alpha))));
    }

    void finish() {
        if (state == CinematicFilterState.OFF || state == CinematicFilterState.FADING_OUT) return;
        begin(CinematicFilterState.FADING_OUT, Math.max(1,
                Math.round(FADE_OUT_TICKS * alpha)));
    }

    void tick() {
        previousAlpha = alpha;
        if (state != CinematicFilterState.FADING_IN && state != CinematicFilterState.FADING_OUT) return;
        transitionTick++;
        float progress = Math.min(1.0F, transitionTick / (float) transitionDuration);
        float eased = progress * progress * (3.0F - 2.0F * progress);
        float target = state == CinematicFilterState.FADING_IN ? 1.0F : 0.0F;
        alpha = transitionStart + (target - transitionStart) * eased;
        if (progress >= 1.0F) {
            alpha = target;
            previousAlpha = alpha;
            state = target > 0.0F ? CinematicFilterState.ACTIVE : CinematicFilterState.OFF;
        }
    }

    void clear() {
        state = CinematicFilterState.OFF;
        alpha = previousAlpha = transitionStart = 0.0F;
        transitionTick = transitionDuration = 0;
    }

    private void begin(CinematicFilterState next, int duration) {
        previousAlpha = alpha;
        transitionStart = alpha;
        transitionTick = 0;
        transitionDuration = duration;
        state = next;
    }
}
