package com.ikunkk02.wishingwillow.network.packet;

import com.ikunkk02.wishingwillow.client.network.ClientPacketHandlers;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public record UnboxingStartedPacket(UUID sessionId, InteractionHand hand,
                                    long itemInstanceId, ItemStack stackSnapshot) {
    public static void encode(UnboxingStartedPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.sessionId);
        buffer.writeEnum(packet.hand);
        buffer.writeLong(packet.itemInstanceId);
        buffer.writeItem(packet.stackSnapshot);
    }

    public static UnboxingStartedPacket decode(FriendlyByteBuf buffer) {
        return new UnboxingStartedPacket(
                buffer.readUUID(), buffer.readEnum(InteractionHand.class),
                buffer.readLong(), buffer.readItem()
        );
    }

    public static void handle(UnboxingStartedPacket packet, Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientPacketHandlers.startUnboxing(packet));
    }
}
