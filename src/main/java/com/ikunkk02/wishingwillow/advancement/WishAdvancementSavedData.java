package com.ikunkk02.wishingwillow.advancement;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class WishAdvancementSavedData extends SavedData {
    private static final String DATA_NAME = "wishing_willow_advancement_progress";
    private final Map<UUID, WishAdvancementProgress> players = new LinkedHashMap<>();

    public static WishAdvancementSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                WishAdvancementSavedData::load, WishAdvancementSavedData::new, DATA_NAME);
    }

    public WishAdvancementProgress progress(UUID playerId) {
        return players.computeIfAbsent(playerId, ignored -> new WishAdvancementProgress());
    }

    public void changed() { setDirty(); }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        players.forEach((playerId, progress) -> {
            CompoundTag entry = progress.save();
            entry.putUUID("Player", playerId);
            list.add(entry);
        });
        tag.put("Players", list);
        return tag;
    }

    public static WishAdvancementSavedData load(CompoundTag tag) {
        WishAdvancementSavedData data = new WishAdvancementSavedData();
        for (Tag value : tag.getList("Players", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) value;
            if (entry.hasUUID("Player")) {
                data.players.put(entry.getUUID("Player"), WishAdvancementProgress.load(entry));
            }
        }
        return data;
    }
}
