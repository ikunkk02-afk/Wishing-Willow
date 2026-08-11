package com.ikunkk02.wishingwillow.client.music;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WishMusicSettingsSnapshotTest {
    @Test void masterSwitchAndBothVolumeControlsAreHardGates() {
        assertFalse(new WishMusicSettingsSnapshot(false, 70, true, true).canPlay(WishMusicScene.WISH_SEQUENCE, 1));
        assertFalse(new WishMusicSettingsSnapshot(true, 0, true, true).canPlay(WishMusicScene.WISH_SEQUENCE, 1));
        assertFalse(new WishMusicSettingsSnapshot(true, 70, true, true).canPlay(WishMusicScene.WISH_SEQUENCE, 0));
        assertTrue(new WishMusicSettingsSnapshot(true, 70, true, true).canPlay(WishMusicScene.WISH_SEQUENCE, 0.5f));
    }

    @Test void sceneSwitchesAreIndependent() {
        WishMusicSettingsSnapshot settings = new WishMusicSettingsSnapshot(true, 70, false, true);
        assertFalse(settings.canPlay(WishMusicScene.TRADE_REVEAL, 1));
        assertTrue(settings.canPlay(WishMusicScene.WISH_SEQUENCE, 1));
    }
}
