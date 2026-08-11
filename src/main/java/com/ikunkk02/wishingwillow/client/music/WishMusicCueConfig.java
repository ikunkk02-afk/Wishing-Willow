package com.ikunkk02.wishingwillow.client.music;

public record WishMusicCueConfig(double snapCueSeconds) {
    public static final WishMusicCueConfig UNALIGNED = new WishMusicCueConfig(-1.0);
    public boolean hasSnapCue() { return Double.isFinite(snapCueSeconds) && snapCueSeconds >= 0; }
}
