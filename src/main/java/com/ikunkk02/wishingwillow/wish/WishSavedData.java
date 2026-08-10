package com.ikunkk02.wishingwillow.wish;

import com.ikunkk02.wishingwillow.ai.AiErrorCategory;
import com.ikunkk02.wishingwillow.ai.InterpretationState;
import com.ikunkk02.wishingwillow.ai.WishInterpretation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class WishSavedData extends SavedData {
    private static final String DATA_NAME = "wishing_willow_wishes";
    private final Map<UUID, WishRecord> wishesBySession = new HashMap<>();

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
            data.wishesBySession.put(record.sessionId(), record);
        }
        return data;
    }

    public void update(WishSession session) {
        WishRecord fresh = WishRecord.fromSession(session);
        WishRecord existing = wishesBySession.get(session.sessionId());
        if (existing != null) {
            fresh = fresh.withPlanning(existing.planState(), existing.planError(), existing.plan());
        }
        update(fresh);
    }

    public void update(WishRecord record) {
        wishesBySession.put(record.sessionId(), record);
        setDirty();
    }

    public boolean updateInterpretation(
            UUID sessionId,
            InterpretationState state,
            AiErrorCategory category,
            @Nullable WishInterpretation interpretation,
            long updatedAt
    ) {
        WishRecord record = wishesBySession.get(sessionId);
        if (record == null) {
            return false;
        }
        update(record.withInterpretation(state, category, interpretation, updatedAt));
        return true;
    }

    @Nullable
    public WishRecord getBySession(UUID sessionId) {
        return wishesBySession.get(sessionId);
    }

    @Nullable
    public WishRecord getLatest(UUID playerId) {
        return wishesBySession.values().stream()
                .filter(record -> record.playerId().equals(playerId))
                .max(Comparator.comparingLong(WishRecord::submittedAtEpochMillis))
                .orElse(null);
    }

    public List<WishRecord> getAll(UUID playerId) {
        return wishesBySession.values().stream()
                .filter(record -> record.playerId().equals(playerId))
                .sorted(Comparator.comparingLong(WishRecord::submittedAtEpochMillis))
                .toList();
    }

    public List<WishRecord> allRecords() {
        return List.copyOf(wishesBySession.values());
    }

    public void failPendingForPlayer(UUID playerId) {
        List<WishRecord> updates = new ArrayList<>();
        for (WishRecord record : wishesBySession.values()) {
            if (record.playerId().equals(playerId)
                    && record.interpretationState() == InterpretationState.REQUESTING) {
                updates.add(record.withInterpretation(
                        InterpretationState.AI_REQUEST_FAILED,
                        AiErrorCategory.DISCONNECTED,
                        null,
                        System.currentTimeMillis()
                ));
            }
        }
        updates.forEach(this::update);
    }

    public void failAllPending() {
        List<WishRecord> updates = wishesBySession.values().stream()
                .filter(record -> record.interpretationState() == InterpretationState.REQUESTING)
                .map(record -> record.withInterpretation(
                        InterpretationState.AI_REQUEST_FAILED,
                        AiErrorCategory.DISCONNECTED,
                        null,
                        System.currentTimeMillis()
                ))
                .toList();
        updates.forEach(this::update);
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag wishes = new ListTag();
        wishesBySession.values().stream()
                .sorted(Comparator.comparing(WishRecord::sessionId))
                .map(WishRecord::save)
                .forEach(wishes::add);
        tag.put("Wishes", wishes);
        return tag;
    }
}
