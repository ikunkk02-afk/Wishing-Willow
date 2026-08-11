package com.ikunkk02.wishingwillow.client.cinematic;

public enum CinematicFilterIntensity {
    LOW(0.70F),
    NORMAL(1.0F),
    HIGH(1.25F);

    private final float scale;

    CinematicFilterIntensity(float scale) {
        this.scale = scale;
    }

    public float scale() {
        return scale;
    }
}
