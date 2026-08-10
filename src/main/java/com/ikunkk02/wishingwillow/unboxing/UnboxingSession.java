package com.ikunkk02.wishingwillow.unboxing;

import net.minecraft.world.InteractionHand;

import java.util.UUID;

public final class UnboxingSession {
    public static final int REMOVE_TICK = 40;
    public static final int FINISH_TICK = 66;

    private final UUID sessionId;
    private final UUID playerId;
    private final InteractionHand hand;
    private final long itemInstanceId;
    private final long startedGameTime;
    private UnboxingState state = UnboxingState.UNOPENED;

    public UnboxingSession(UUID sessionId, UUID playerId, InteractionHand hand,
                           long itemInstanceId, long startedGameTime) {
        this.sessionId = sessionId;
        this.playerId = playerId;
        this.hand = hand;
        this.itemInstanceId = itemInstanceId;
        this.startedGameTime = startedGameTime;
    }

    public UUID sessionId() {
        return sessionId;
    }

    public UUID playerId() {
        return playerId;
    }

    public InteractionHand hand() {
        return hand;
    }

    public long itemInstanceId() {
        return itemInstanceId;
    }

    public long startedGameTime() {
        return startedGameTime;
    }

    public UnboxingState state() {
        return state;
    }

    public long elapsed(long gameTime) {
        return Math.max(0L, gameTime - startedGameTime);
    }

    public boolean readyToRemove(long gameTime) {
        return state == UnboxingState.UNBOXING && elapsed(gameTime) >= REMOVE_TICK;
    }

    public boolean readyToFinish(long gameTime) {
        return state == UnboxingState.WILLOW_REMOVED && elapsed(gameTime) >= FINISH_TICK;
    }

    public boolean transition(UnboxingState expected, UnboxingState next) {
        if (state != expected || state.terminal()) {
            return false;
        }
        state = next;
        return true;
    }
}
