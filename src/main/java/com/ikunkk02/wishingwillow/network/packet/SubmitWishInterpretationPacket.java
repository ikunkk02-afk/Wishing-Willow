package com.ikunkk02.wishingwillow.network.packet;

import com.ikunkk02.wishingwillow.ai.AiErrorCategory;
import com.ikunkk02.wishingwillow.ai.InterpretationState;
import com.ikunkk02.wishingwillow.ai.WishInterpretation;
import com.ikunkk02.wishingwillow.ai.WishInterpretationValidator;
import com.ikunkk02.wishingwillow.ai.WishRejection;
import com.ikunkk02.wishingwillow.ai.WishRejectionCode;
import com.ikunkk02.wishingwillow.wish.WishManager;
import com.ikunkk02.wishingwillow.program.WishProgram;
import com.ikunkk02.wishingwillow.program.WishProgramJson;
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
        @Nullable WishInterpretation interpretation,
        @Nullable WishProgram program,
        @Nullable WishRejection rejection
) {
    public SubmitWishInterpretationPacket(UUID sessionId, InterpretationState state,
                                          AiErrorCategory errorCategory,
                                          @Nullable WishInterpretation interpretation) {
        this(sessionId, state, errorCategory, interpretation, null, null);
    }

    public SubmitWishInterpretationPacket(UUID sessionId, InterpretationState state,
                                          AiErrorCategory errorCategory,
                                          @Nullable WishInterpretation interpretation,
                                          @Nullable WishProgram program) {
        this(sessionId, state, errorCategory, interpretation, program, null);
    }
    public static void encode(SubmitWishInterpretationPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.sessionId);
        buffer.writeEnum(packet.state);
        buffer.writeEnum(packet.errorCategory);
        buffer.writeBoolean(packet.interpretation != null);
        if (packet.interpretation != null) {
            buffer.writeUtf(WishInterpretationValidator.toJson(packet.interpretation), 32 * 1024);
        }
        buffer.writeBoolean(packet.program != null);
        if (packet.program != null) buffer.writeUtf(WishProgramJson.toJson(packet.program), WishProgramJson.MAX_JSON);
        buffer.writeBoolean(packet.rejection != null);
        if (packet.rejection != null) {
            buffer.writeEnum(packet.rejection.code());
            buffer.writeUtf(packet.rejection.playerMessage(), WishRejection.MAX_PLAYER_MESSAGE_LENGTH);
            buffer.writeUtf(packet.rejection.reason(), WishRejection.MAX_REASON_LENGTH);
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
        WishProgram program = buffer.readBoolean()
                ? WishProgramJson.parseAndValidate(buffer.readUtf(WishProgramJson.MAX_JSON)) : null;
        WishRejection rejection = buffer.readBoolean()
                ? WishRejection.sanitized(buffer.readEnum(WishRejectionCode.class),
                buffer.readUtf(WishRejection.MAX_PLAYER_MESSAGE_LENGTH),
                buffer.readUtf(WishRejection.MAX_REASON_LENGTH)) : null;
        return new SubmitWishInterpretationPacket(sessionId, state, errorCategory, interpretation, program, rejection);
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
                    packet.interpretation,
                    packet.program,
                    packet.rejection
            );
        }
    }
}
