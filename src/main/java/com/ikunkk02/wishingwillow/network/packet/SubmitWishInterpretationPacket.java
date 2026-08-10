package com.ikunkk02.wishingwillow.network.packet;

import com.ikunkk02.wishingwillow.ai.AiErrorCategory;
import com.ikunkk02.wishingwillow.ai.InterpretationState;
import com.ikunkk02.wishingwillow.ai.WishCapability;
import com.ikunkk02.wishingwillow.ai.WishDelivery;
import com.ikunkk02.wishingwillow.ai.WishInterpretation;
import com.ikunkk02.wishingwillow.ai.WishInterpretationValidator;
import com.ikunkk02.wishingwillow.ai.WishTone;
import com.ikunkk02.wishingwillow.wish.WishManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
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
            WishInterpretation interpretation = packet.interpretation;
            buffer.writeVarInt(interpretation.schemaVersion());
            buffer.writeUtf(interpretation.intent(), WishInterpretationValidator.MAX_INTENT_LENGTH);
            buffer.writeUtf(interpretation.literalGoal(), WishInterpretationValidator.MAX_LITERAL_GOAL_LENGTH);
            buffer.writeUtf(interpretation.loophole(), WishInterpretationValidator.MAX_TEXT_LENGTH);
            buffer.writeUtf(interpretation.twistedOutcome(), WishInterpretationValidator.MAX_TEXT_LENGTH);
            buffer.writeUtf(interpretation.reasoningSummary(), WishInterpretationValidator.MAX_TEXT_LENGTH);
            buffer.writeEnum(interpretation.tone());
            buffer.writeVarInt(interpretation.severity());
            buffer.writeEnum(interpretation.delivery());
            buffer.writeVarInt(interpretation.requiredCapabilities().size());
            interpretation.requiredCapabilities().forEach(buffer::writeEnum);
        }
    }

    public static SubmitWishInterpretationPacket decode(FriendlyByteBuf buffer) {
        UUID sessionId = buffer.readUUID();
        InterpretationState state = buffer.readEnum(InterpretationState.class);
        AiErrorCategory errorCategory = buffer.readEnum(AiErrorCategory.class);
        WishInterpretation interpretation = null;
        if (buffer.readBoolean()) {
            int schemaVersion = buffer.readVarInt();
            String intent = buffer.readUtf(WishInterpretationValidator.MAX_INTENT_LENGTH);
            String literalGoal = buffer.readUtf(WishInterpretationValidator.MAX_LITERAL_GOAL_LENGTH);
            String loophole = buffer.readUtf(WishInterpretationValidator.MAX_TEXT_LENGTH);
            String twistedOutcome = buffer.readUtf(WishInterpretationValidator.MAX_TEXT_LENGTH);
            String reasoningSummary = buffer.readUtf(WishInterpretationValidator.MAX_TEXT_LENGTH);
            WishTone tone = buffer.readEnum(WishTone.class);
            int severity = buffer.readVarInt();
            WishDelivery delivery = buffer.readEnum(WishDelivery.class);
            int count = buffer.readVarInt();
            if (count < 1 || count > WishInterpretationValidator.MAX_CAPABILITIES) {
                throw new IllegalArgumentException("Invalid capability count");
            }
            List<WishCapability> capabilities = new ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                capabilities.add(buffer.readEnum(WishCapability.class));
            }
            interpretation = new WishInterpretation(
                    schemaVersion, intent, literalGoal, loophole, twistedOutcome,
                    reasoningSummary, tone, severity, delivery, capabilities
            );
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
