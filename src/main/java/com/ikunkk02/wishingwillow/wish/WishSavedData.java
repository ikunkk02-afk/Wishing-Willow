package com.ikunkk02.wishingwillow.wish;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class WishSavedData extends SavedData {
    private static final String DATA_NAME = "wishing_willow_wishes";
    private final Map<UUID, WishRecord> latestWishes = new HashMap<>();

    public static WishSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                WishSavedData::load,
                WishSavedData::new,
                DATA_NAME
        );
    }

    public static WishSavedData load(CompoundTag tag) {
        WishSavedData data = new WishSavedData();
        ListTag wishes = tag.getList("Wishes", Tag.TAG_COMPOUND);
        for (Tag entry : wishes) {
            WishRecord record = WishRecord.load((CompoundTag) entry);
            data.latestWishes.put(record.playerId(), record);
        }
        return data;
    }

    public void update(WishSession session) {
        latestWishes.put(session.playerId(), WishRecord.fromSession(session));
        setDirty();
    }

    @Nullable
    public WishRecord getLatest(UUID playerId) {
        return latestWishes.get(playerId);
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag wishes = new ListTag();
        latestWishes.values().stream()
                .sorted((left, right) -> left.playerId().compareTo(right.playerId()))
                .map(WishRecord::save)
                .forEach(wishes::add);
        tag.put("Wishes", wishes);
        return tag;
    }
}
