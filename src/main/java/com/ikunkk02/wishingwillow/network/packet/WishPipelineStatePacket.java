package com.ikunkk02.wishingwillow.network.packet;

import com.ikunkk02.wishingwillow.client.network.ClientPacketHandlers;
import com.ikunkk02.wishingwillow.wish.WishPipelineState;
import com.ikunkk02.wishingwillow.wish.WishSessionTerminationReason;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** Server-authoritative business pipeline progress; never represents cinematic completion. */
public record WishPipelineStatePacket(UUID sessionId, WishPipelineState state,
                                      WishSessionTerminationReason reason, String detail) {
    public WishPipelineStatePacket {
        detail = detail == null ? "" : detail;
        if (detail.length() > 256) detail = detail.substring(0, 256);
    }

    public static WishPipelineStatePacket progress(UUID sessionId, WishPipelineState state) {
        if (state.terminal()) throw new IllegalArgumentException("TERMINAL_STATE_REQUIRES_REASON");
        return new WishPipelineStatePacket(sessionId, state, WishSessionTerminationReason.NONE, "");
    }

    public static WishPipelineStatePacket terminal(UUID sessionId, WishPipelineState state,
                                                   WishSessionTerminationReason reason, String detail) {
        if (!state.terminal() || reason == WishSessionTerminationReason.NONE) {
            throw new IllegalArgumentException("INVALID_TERMINAL_PIPELINE_STATE");
        }
        return new WishPipelineStatePacket(sessionId, state, reason, detail);
    }

    public static void encode(WishPipelineStatePacket packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.sessionId);
        buffer.writeEnum(packet.state);
        buffer.writeEnum(packet.reason);
        buffer.writeUtf(packet.detail, 256);
    }

    public static WishPipelineStatePacket decode(FriendlyByteBuf buffer) {
        return new WishPipelineStatePacket(buffer.readUUID(), buffer.readEnum(WishPipelineState.class),
                buffer.readEnum(WishSessionTerminationReason.class), buffer.readUtf(256));
    }

    public static void handle(WishPipelineStatePacket packet, Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientPacketHandlers.updateWishPipelineState(packet));
    }
}
