package com.ikunkk02.wishingwillow.music;

import net.minecraft.nbt.CompoundTag;

/** Per-player, per-save discovery marker stored inside Forge's persisted player data. */
public final class TradeRevealMusicState {
    private static final String SEEN_KEY = "WishingWillowTradeRevealMusicSeen";
    private TradeRevealMusicState() {}
    public static boolean markFirstDiscovery(CompoundTag persistedPlayerData) {
        if (persistedPlayerData.getBoolean(SEEN_KEY)) return false;
        persistedPlayerData.putBoolean(SEEN_KEY, true);
        return true;
    }
}
