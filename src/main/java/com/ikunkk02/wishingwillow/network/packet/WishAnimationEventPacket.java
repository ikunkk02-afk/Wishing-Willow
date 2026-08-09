package com.ikunkk02.wishingwillow.network.packet;

import com.ikunkk02.wishingwillow.wish.WishAnimationEvent;
import com.ikunkk02.wishingwillow.wish.WishManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public record WishAnimationEventPacket(UUID sessionId, WishAnimationEvent event) {
    public static void encode(WishAnimationEventPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.sessionId);
        buffer.writeEnum(packet.event);
    }

    public static WishAnimationEventPacket decode(FriendlyByteBuf buffer) {
        return new WishAnimationEventPacket(
                buffer.readUUID(),
                buffer.readEnum(WishAnimationEvent.class)
        );
    }

    public static void handle(WishAnimationEventPacket packet, Supplier<NetworkEvent.Context> context) {
        ServerPlayer sender = context.get().getSender();
        if (sender != null) {
            WishManager.handleAnimationEvent(sender, packet.sessionId, packet.event);
        }
    }
}
