package com.ikunkk02.wishingwillow.network.packet;

import com.ikunkk02.wishingwillow.client.network.ClientPacketHandlers;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record OpenWishScreenPacket(InteractionHand hand) {
    public static void encode(OpenWishScreenPacket packet, FriendlyByteBuf buffer) {
        buffer.writeEnum(packet.hand);
    }

    public static OpenWishScreenPacket decode(FriendlyByteBuf buffer) {
        return new OpenWishScreenPacket(buffer.readEnum(InteractionHand.class));
    }

    public static void handle(OpenWishScreenPacket packet, Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandlers.openWishScreen(packet));
    }
}
