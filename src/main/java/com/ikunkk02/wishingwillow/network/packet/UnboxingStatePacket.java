package com.ikunkk02.wishingwillow.network.packet;

import com.ikunkk02.wishingwillow.client.network.ClientPacketHandlers;
import com.ikunkk02.wishingwillow.unboxing.UnboxingState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public record UnboxingStatePacket(UUID sessionId, UnboxingState state) {
    public static void encode(UnboxingStatePacket packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.sessionId);
        buffer.writeEnum(packet.state);
    }

    public static UnboxingStatePacket decode(FriendlyByteBuf buffer) {
        return new UnboxingStatePacket(buffer.readUUID(), buffer.readEnum(UnboxingState.class));
    }

    public static void handle(UnboxingStatePacket packet, Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientPacketHandlers.updateUnboxing(packet));
    }
}
