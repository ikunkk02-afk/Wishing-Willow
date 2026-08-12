package com.ikunkk02.wishingwillow.payment;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;
import java.util.UUID;

public final class WishPaymentManager {
    @FunctionalInterface
    public interface RefundDelivery {
        boolean deliver(UUID playerId, ItemStack payment);
    }

    private final WishPaymentSavedData data;

    public WishPaymentManager(WishPaymentSavedData data) {
        this.data = Objects.requireNonNull(data, "data");
    }

    public static WishPaymentManager get(MinecraftServer server) {
        return new WishPaymentManager(WishPaymentSavedData.get(server));
    }

    public WishPaymentReceipt reserve(UUID sessionId, UUID playerId, ItemStack payment) {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(payment, "payment");
        WishPaymentReceipt existing = data.get(sessionId);
        if (existing != null) return existing;
        if (payment.isEmpty()) throw new IllegalArgumentException("payment must not be empty");
        WishPaymentReceipt receipt = new WishPaymentReceipt(sessionId, playerId, payment);
        data.put(receipt);
        return receipt;
    }

    public boolean markSideEffects(UUID sessionId) {
        WishPaymentReceipt receipt = data.get(sessionId);
        if (receipt == null || !receipt.markSideEffects()) return false;
        data.changed();
        return true;
    }

    public boolean requestRefund(UUID sessionId, RefundDelivery delivery) {
        Objects.requireNonNull(delivery, "delivery");
        WishPaymentReceipt receipt = data.get(sessionId);
        if (receipt == null || !receipt.queueRefund()) return false;
        data.changed();
        if (delivery.deliver(receipt.playerId(), receipt.payment())) {
            receipt.completeRefund();
            data.changed();
        }
        return true;
    }

    public boolean requestRefund(MinecraftServer server, UUID sessionId) {
        return requestRefund(sessionId, serverDelivery(server));
    }

    public boolean deliverPending(UUID sessionId, RefundDelivery delivery) {
        Objects.requireNonNull(delivery, "delivery");
        WishPaymentReceipt receipt = data.get(sessionId);
        if (receipt == null || !receipt.pending() || receipt.refunded()) return false;
        if (!delivery.deliver(receipt.playerId(), receipt.payment())) return false;
        if (!receipt.completeRefund()) return false;
        data.changed();
        return true;
    }

    public int deliverPending(MinecraftServer server, UUID playerId) {
        RefundDelivery delivery = serverDelivery(server);
        int delivered = 0;
        for (WishPaymentReceipt receipt : data.pendingFor(playerId)) {
            if (deliverPending(receipt.sessionId(), delivery)) delivered++;
        }
        return delivered;
    }

    private static RefundDelivery serverDelivery(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        return (playerId, payment) -> {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null) return false;
            ItemStack remainder = payment.copy();
            player.getInventory().add(remainder);
            if (!remainder.isEmpty()) player.drop(remainder, false);
            return true;
        };
    }
}
