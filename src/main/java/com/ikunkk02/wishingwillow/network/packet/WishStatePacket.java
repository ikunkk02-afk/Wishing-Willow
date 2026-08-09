package com.ikunkk02.wishingwillow.network.packet;

import com.ikunkk02.wishingwillow.client.network.ClientPacketHandlers;
import com.ikunkk02.wishingwillow.wish.WishRejectionReason;
import com.ikunkk02.wishingwillow.wish.WishState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public record WishStatePacket(UUID correlationId, WishState state, WishRejectionReason reason) {
    public static void encode(WishStatePacket packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.correlationId);
        buffer.writeEnum(packet.state);
        buffer.writeEnum(packet.reason);
    }

    public static WishStatePacket decode(FriendlyByteBuf buffer) {
        return new WishStatePacket(
                buffer.readUUID(),
                buffer.readEnum(WishState.class),
                buffer.readEnum(WishRejectionReason.class)
        );
    }

    public static void handle(WishStatePacket packet, Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandlers.updateWishState(packet));
    }
}
