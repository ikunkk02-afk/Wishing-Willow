package com.ikunkk02.wishingwillow.wish;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record WishRecord(
        UUID sessionId,
        UUID playerId,
        String rawWish,
        ResourceLocation dimension,
        long submittedGameTime,
        long submittedAtEpochMillis,
        WishState state
) {
    public static WishRecord fromSession(WishSession session) {
        return new WishRecord(
                session.sessionId(),
                session.playerId(),
                session.rawWish(),
                session.dimension(),
                session.submittedGameTime(),
                session.submittedAtEpochMillis(),
                session.state()
        );
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("SessionId", sessionId);
        tag.putUUID("PlayerId", playerId);
        tag.putString("Wish", rawWish);
        tag.putString("Dimension", dimension.toString());
        tag.putLong("GameTime", submittedGameTime);
        tag.putLong("SubmittedAt", submittedAtEpochMillis);
        tag.putString("State", state.name());
        return tag;
    }

    public static WishRecord load(CompoundTag tag) {
        WishState loadedState;
        try {
            loadedState = WishState.valueOf(tag.getString("State"));
        } catch (IllegalArgumentException exception) {
            loadedState = WishState.CANCELLED;
        }
        if (loadedState == WishState.REQUESTED || loadedState == WishState.ANIMATING) {
            loadedState = WishState.CANCELLED;
        } else if (loadedState == WishState.SNAPPED) {
            loadedState = WishState.FINISHED;
        }
        return new WishRecord(
                tag.getUUID("SessionId"),
                tag.getUUID("PlayerId"),
                tag.getString("Wish"),
                new ResourceLocation(tag.getString("Dimension")),
                tag.getLong("GameTime"),
                tag.getLong("SubmittedAt"),
                loadedState
        );
    }
}
