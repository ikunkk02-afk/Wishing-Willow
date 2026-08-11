package com.ikunkk02.wishingwillow.network.packet;

import com.ikunkk02.wishingwillow.ai.AiErrorCategory;
import com.ikunkk02.wishingwillow.ai.InterpretationState;
import com.ikunkk02.wishingwillow.ai.WishInterpretation;
import com.ikunkk02.wishingwillow.ai.WishInterpretationValidator;
import com.ikunkk02.wishingwillow.wish.WishManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import javax.annotation.Nullable;
import java.util.UUID;
import java.util.function.Supplier;

public record SubmitWishInterpretationPacket(
        UUID sessionId,
        InterpretationState state,
        AiErrorCategory errorCategory,
        @Nullable WishInterpretation interpretation
) {
    public static void encode(SubmitWishInterpretationPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.sessionId);
        buffer.writeEnum(packet.state);
        buffer.writeEnum(packet.errorCategory);
        buffer.writeBoolean(packet.interpretation != null);
        if (packet.interpretation != null) {
            buffer.writeUtf(WishInterpretationValidator.toJson(packet.interpretation), 32 * 1024);
        }
    }

    public static SubmitWishInterpretationPacket decode(FriendlyByteBuf buffer) {
        UUID sessionId = buffer.readUUID();
        InterpretationState state = buffer.readEnum(InterpretationState.class);
        AiErrorCategory errorCategory = buffer.readEnum(AiErrorCategory.class);
        WishInterpretation interpretation = null;
        if (buffer.readBoolean()) {
            interpretation = WishInterpretationValidator.parseAndValidate(buffer.readUtf(32 * 1024));
        }
        return new SubmitWishInterpretationPacket(sessionId, state, errorCategory, interpretation);
    }

    public static void handle(
            SubmitWishInterpretationPacket packet,
            Supplier<NetworkEvent.Context> context
    ) {
        ServerPlayer sender = context.get().getSender();
        if (sender != null) {
            WishManager.handleInterpretationResult(
                    sender,
                    packet.sessionId,
                    packet.state,
                    packet.errorCategory,
                    packet.interpretation
            );
        }
    }
}
