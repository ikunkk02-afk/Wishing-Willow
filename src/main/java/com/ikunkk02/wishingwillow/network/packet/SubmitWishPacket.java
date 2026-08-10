package com.ikunkk02.wishingwillow.network.packet;

import com.ikunkk02.wishingwillow.ai.AiConfig;
import com.ikunkk02.wishingwillow.ai.AiExecutionMode;
import com.ikunkk02.wishingwillow.ai.AiProviderType;
import com.ikunkk02.wishingwillow.wish.WishManager;
import com.ikunkk02.wishingwillow.wish.WishTextValidator;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public record SubmitWishPacket(
        UUID requestId,
        InteractionHand hand,
        String wish,
        AiExecutionMode executionMode,
        AiProviderType providerType,
        String model
) {
    public static void encode(SubmitWishPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.requestId);
        buffer.writeEnum(packet.hand);
        buffer.writeUtf(packet.wish, WishTextValidator.MAX_LENGTH);
        buffer.writeEnum(packet.executionMode);
        buffer.writeEnum(packet.providerType);
        buffer.writeUtf(packet.model, AiConfig.MAX_MODEL_LENGTH);
    }

    public static SubmitWishPacket decode(FriendlyByteBuf buffer) {
        return new SubmitWishPacket(
                buffer.readUUID(),
                buffer.readEnum(InteractionHand.class),
                buffer.readUtf(WishTextValidator.MAX_LENGTH),
                buffer.readEnum(AiExecutionMode.class),
                buffer.readEnum(AiProviderType.class),
                buffer.readUtf(AiConfig.MAX_MODEL_LENGTH)
        );
    }

    public static void handle(SubmitWishPacket packet, Supplier<NetworkEvent.Context> context) {
        ServerPlayer sender = context.get().getSender();
        if (sender != null) {
            WishManager.submit(
                    sender, packet.requestId, packet.hand, packet.wish,
                    packet.executionMode, packet.providerType, packet.model
            );
        }
    }
}
