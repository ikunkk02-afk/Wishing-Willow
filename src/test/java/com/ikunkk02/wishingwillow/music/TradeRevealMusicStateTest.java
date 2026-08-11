package com.ikunkk02.wishingwillow.music;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TradeRevealMusicStateTest {
    @Test void aWorldDiscoveryCanOnlyBeMarkedOnce() {
        CompoundTag persisted = new CompoundTag();
        assertTrue(TradeRevealMusicState.markFirstDiscovery(persisted));
        assertFalse(TradeRevealMusicState.markFirstDiscovery(persisted));
        CompoundTag anotherWorld = new CompoundTag();
        assertTrue(TradeRevealMusicState.markFirstDiscovery(anotherWorld));
    }
}
