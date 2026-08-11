package com.ikunkk02.wishingwillow.network.packet;

import com.ikunkk02.wishingwillow.client.network.ClientPacketHandlers;
import com.ikunkk02.wishingwillow.omen.WishOmen;
import com.ikunkk02.wishingwillow.omen.WishOmenCategory;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public record WishOmenPacket(UUID sessionId, WishOmenCategory category, String translationKey, int delayTicks) {
    public WishOmenPacket(WishOmen omen) {
        this(omen.sessionId(), omen.category(), omen.translationKey(), omen.delayTicks());
    }

    public static void encode(WishOmenPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.sessionId);
        buffer.writeEnum(packet.category);
        buffer.writeUtf(packet.translationKey, 96);
        buffer.writeVarInt(packet.delayTicks);
    }

    public static WishOmenPacket decode(FriendlyByteBuf buffer) {
        return new WishOmenPacket(buffer.readUUID(), buffer.readEnum(WishOmenCategory.class),
                buffer.readUtf(96), buffer.readVarInt());
    }

    public static void handle(WishOmenPacket packet, Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandlers.receiveOmen(packet));
    }
}
