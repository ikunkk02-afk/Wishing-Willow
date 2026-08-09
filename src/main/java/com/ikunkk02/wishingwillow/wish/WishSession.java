package com.ikunkk02.wishingwillow.wish;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;

import java.util.UUID;

public final class WishSession {
    private final UUID sessionId;
    private final UUID playerId;
    private final String rawWish;
    private final ResourceLocation dimension;
    private final long submittedGameTime;
    private final long submittedAtEpochMillis;
    private final InteractionHand hand;
    private final long itemInstanceId;
    private WishState state;
    private long stateChangedGameTime;

    public WishSession(
            UUID sessionId,
            UUID playerId,
            String rawWish,
            ResourceLocation dimension,
            long submittedGameTime,
            long submittedAtEpochMillis,
            InteractionHand hand,
            long itemInstanceId
    ) {
        this.sessionId = sessionId;
        this.playerId = playerId;
        this.rawWish = rawWish;
        this.dimension = dimension;
        this.submittedGameTime = submittedGameTime;
        this.submittedAtEpochMillis = submittedAtEpochMillis;
        this.hand = hand;
        this.itemInstanceId = itemInstanceId;
        this.state = WishState.REQUESTED;
        this.stateChangedGameTime = submittedGameTime;
    }

    public UUID sessionId() {
        return sessionId;
    }

    public UUID playerId() {
        return playerId;
    }

    public String rawWish() {
        return rawWish;
    }

    public ResourceLocation dimension() {
        return dimension;
    }

    public long submittedGameTime() {
        return submittedGameTime;
    }

    public long submittedAtEpochMillis() {
        return submittedAtEpochMillis;
    }

    public InteractionHand hand() {
        return hand;
    }

    public long itemInstanceId() {
        return itemInstanceId;
    }

    public WishState state() {
        return state;
    }

    public long stateChangedGameTime() {
        return stateChangedGameTime;
    }

    public void transitionTo(WishState newState, long gameTime) {
        state = newState;
        stateChangedGameTime = gameTime;
    }
}
