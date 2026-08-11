package com.ikunkk02.wishingwillow.network.packet;

import com.ikunkk02.wishingwillow.program.WishProgramError;
import com.ikunkk02.wishingwillow.wish.WishManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import javax.annotation.Nullable;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * NEW path submission packet: carries the final WishProgram JSON (and schema version) from the
 * client to the server. The server re-validates everything — schema, registry, policy, budget —
 * before starting native program execution. The interpretation stays on the server; it is never
 * re-transmitted.
 */
public record SubmitWishProgramPacket(UUID sessionId, UUID attemptId, int schemaVersion,
                                      WishProgramError error, @Nullable String programJson) {
    public static SubmitWishProgramPacket success(UUID sessionId, UUID attemptId, int schemaVersion,
                                                  String programJson) {
        return new SubmitWishProgramPacket(sessionId, attemptId, schemaVersion,
                WishProgramError.UNKNOWN, programJson);
    }

    public static SubmitWishProgramPacket failure(UUID sessionId, UUID attemptId, int schemaVersion,
                                                  WishProgramError error) {
        return new SubmitWishProgramPacket(sessionId, attemptId, schemaVersion, error, null);
    }

    public static void encode(SubmitWishProgramPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.sessionId);
        buffer.writeUUID(packet.attemptId);
        buffer.writeInt(packet.schemaVersion);
        buffer.writeEnum(packet.error);
        buffer.writeBoolean(packet.programJson != null);
        if (packet.programJson != null) {
            buffer.writeUtf(packet.programJson, 64 * 1024);
        }
    }

    public static SubmitWishProgramPacket decode(FriendlyByteBuf buffer) {
        UUID session = buffer.readUUID();
        UUID attempt = buffer.readUUID();
        int schemaVersion = buffer.readInt();
        WishProgramError error = buffer.readEnum(WishProgramError.class);
        String programJson = buffer.readBoolean() ? buffer.readUtf(64 * 1024) : null;
        return new SubmitWishProgramPacket(session, attempt, schemaVersion, error, programJson);
    }

    public static void handle(SubmitWishProgramPacket packet, Supplier<NetworkEvent.Context> context) {
        ServerPlayer sender = context.get().getSender();
        if (sender != null) {
            WishManager.handleProgramSubmission(sender, packet.sessionId(), packet.attemptId(),
                    packet.schemaVersion(), packet.error(), packet.programJson());
        }
    }
}
