package com.ikunkk02.wishingwillow.network.packet;

import com.ikunkk02.wishingwillow.ai.AiConfig;
import com.ikunkk02.wishingwillow.ai.AiProviderType;
import com.ikunkk02.wishingwillow.client.network.ClientPacketHandlers;
import com.ikunkk02.wishingwillow.wish.WishTextValidator;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public record WishStartedPacket(
        UUID sessionId,
        InteractionHand hand,
        long itemInstanceId,
        ItemStack stackSnapshot,
        String originalWish,
        AiProviderType providerType,
        String model
) {
    public static void encode(WishStartedPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.sessionId);
        buffer.writeEnum(packet.hand);
        buffer.writeLong(packet.itemInstanceId);
        buffer.writeItem(packet.stackSnapshot);
        buffer.writeUtf(packet.originalWish, WishTextValidator.MAX_LENGTH);
        buffer.writeEnum(packet.providerType);
        buffer.writeUtf(packet.model, AiConfig.MAX_MODEL_LENGTH);
    }

    public static WishStartedPacket decode(FriendlyByteBuf buffer) {
        return new WishStartedPacket(
                buffer.readUUID(),
                buffer.readEnum(InteractionHand.class),
                buffer.readLong(),
                buffer.readItem(),
                buffer.readUtf(WishTextValidator.MAX_LENGTH),
                buffer.readEnum(AiProviderType.class),
                buffer.readUtf(AiConfig.MAX_MODEL_LENGTH)
        );
    }

    public static void handle(WishStartedPacket packet, Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandlers.startWish(packet));
    }
}
