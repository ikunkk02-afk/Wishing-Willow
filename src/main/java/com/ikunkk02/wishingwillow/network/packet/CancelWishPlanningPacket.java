package com.ikunkk02.wishingwillow.network.packet;

import com.ikunkk02.wishingwillow.wish.WishManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** Ends a superseded client planning attempt so the server record cannot remain in PLANNING. */
public record CancelWishPlanningPacket(UUID sessionId, UUID attemptId) {
    public static void encode(CancelWishPlanningPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.sessionId());
        buffer.writeUUID(packet.attemptId());
    }

    public static CancelWishPlanningPacket decode(FriendlyByteBuf buffer) {
        return new CancelWishPlanningPacket(buffer.readUUID(), buffer.readUUID());
    }

    public static void handle(CancelWishPlanningPacket packet, Supplier<NetworkEvent.Context> context) {
        ServerPlayer sender = context.get().getSender();
        if (sender != null) WishManager.handlePlanningCancellation(sender, packet.sessionId(), packet.attemptId());
    }
}
