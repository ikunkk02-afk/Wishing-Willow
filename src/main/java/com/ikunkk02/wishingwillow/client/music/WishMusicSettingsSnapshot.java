package com.ikunkk02.wishingwillow.client.music;

public record WishMusicSettingsSnapshot(boolean enabled, int volumePercent,
                                        boolean tradeReveal, boolean wishSequence) {
    public boolean canPlay(WishMusicScene scene, float minecraftMusicVolume) {
        return enabled && volumePercent > 0 && minecraftMusicVolume > 0
                && (scene == WishMusicScene.TRADE_REVEAL ? tradeReveal : wishSequence);
    }
}
