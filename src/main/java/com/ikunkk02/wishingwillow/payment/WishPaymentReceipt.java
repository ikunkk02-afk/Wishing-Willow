package com.ikunkk02.wishingwillow.payment;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;
import java.util.UUID;

public final class WishPaymentReceipt {
    private static final String SESSION = "SessionId";
    private static final String PLAYER = "PlayerId";
    private static final String PAYMENT = "Payment";
    private static final String CONSUMED = "Consumed";
    private static final String COMMITTED = "Committed";
    private static final String REFUNDED = "Refunded";
    private static final String PENDING = "Pending";
    private static final String SIDE_EFFECTS = "SideEffectsStarted";

    private final UUID sessionId;
    private final UUID playerId;
    private final ItemStack payment;
    private boolean consumed;
    private boolean committed;
    private boolean refunded;
    private boolean pending;
    private boolean sideEffectsStarted;

    WishPaymentReceipt(UUID sessionId, UUID playerId, ItemStack payment) {
        this(sessionId, playerId, payment, true, false, false, false, false);
    }

    private WishPaymentReceipt(UUID sessionId, UUID playerId, ItemStack payment,
                               boolean consumed, boolean committed, boolean refunded,
                               boolean pending, boolean sideEffectsStarted) {
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId");
        this.playerId = Objects.requireNonNull(playerId, "playerId");
        this.payment = Objects.requireNonNull(payment, "payment").copy();
        this.payment.setCount(1);
        this.consumed = consumed;
        this.committed = committed;
        this.refunded = refunded;
        this.pending = pending;
        this.sideEffectsStarted = sideEffectsStarted;
    }

    static WishPaymentReceipt load(CompoundTag tag) {
        return new WishPaymentReceipt(
                tag.getUUID(SESSION), tag.getUUID(PLAYER), ItemStack.of(tag.getCompound(PAYMENT)),
                tag.getBoolean(CONSUMED), tag.getBoolean(COMMITTED), tag.getBoolean(REFUNDED),
                tag.getBoolean(PENDING), tag.getBoolean(SIDE_EFFECTS));
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID(SESSION, sessionId);
        tag.putUUID(PLAYER, playerId);
        tag.put(PAYMENT, payment.save(new CompoundTag()));
        tag.putBoolean(CONSUMED, consumed);
        tag.putBoolean(COMMITTED, committed);
        tag.putBoolean(REFUNDED, refunded);
        tag.putBoolean(PENDING, pending);
        tag.putBoolean(SIDE_EFFECTS, sideEffectsStarted);
        return tag;
    }

    boolean markSideEffects() {
        if (sideEffectsStarted || refunded || pending) return false;
        sideEffectsStarted = true;
        committed = true;
        return true;
    }

    boolean queueRefund() {
        if (committed || refunded || pending || !consumed) return false;
        pending = true;
        return true;
    }

    boolean completeRefund() {
        if (!pending || refunded || committed) return false;
        pending = false;
        refunded = true;
        return true;
    }

    public UUID sessionId() { return sessionId; }
    public UUID playerId() { return playerId; }
    public ItemStack payment() { return payment.copy(); }
    public boolean consumed() { return consumed; }
    public boolean committed() { return committed; }
    public boolean refunded() { return refunded; }
    public boolean pending() { return pending; }
    public boolean sideEffectsStarted() { return sideEffectsStarted; }
}
