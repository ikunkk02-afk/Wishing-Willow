package com.ikunkk02.wishingwillow.network.packet;

import com.ikunkk02.wishingwillow.agent.core.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

public record WishAgentDebugPacket(WishAgentDebugSnapshot snapshot) {
    public static void encode(WishAgentDebugPacket packet, FriendlyByteBuf buffer) {
        WishAgentDebugSnapshot value = packet.snapshot;
        buffer.writeUUID(value.sessionId()); buffer.writeEnum(value.mode()); buffer.writeEnum(value.state());
        buffer.writeVarInt(value.iterations()); buffer.writeVarInt(value.toolCalls());
        buffer.writeVarInt(value.toolsUsed().size());
        value.toolsUsed().forEach(tool -> buffer.writeUtf(tool, 64));
        buffer.writeUtf(value.lastTool(), 64); buffer.writeUtf(value.lastToolStatus(), 96);
        buffer.writeEnum(value.verificationState()); buffer.writeEnum(value.finalizationState());
        buffer.writeEnum(value.fallbackReason()); buffer.writeVarLong(value.elapsedMs());
    }
    public static WishAgentDebugPacket decode(FriendlyByteBuf buffer) {
        UUID session = buffer.readUUID(); WishPlanningMode mode = buffer.readEnum(WishPlanningMode.class);
        WishAgentDebugState state = buffer.readEnum(WishAgentDebugState.class);
        int iterations = buffer.readVarInt(); int calls = buffer.readVarInt();
        int count = buffer.readVarInt(); if (count < 0 || count > 48) throw new IllegalArgumentException("INVALID_DEBUG_PACKET");
        List<String> tools = new ArrayList<>(); for (int i = 0; i < count; i++) tools.add(buffer.readUtf(64));
        String lastTool = buffer.readUtf(64); String lastStatus = buffer.readUtf(96);
        return new WishAgentDebugPacket(new WishAgentDebugSnapshot(session, mode, state, iterations, calls, tools,
                lastTool, lastStatus, buffer.readEnum(WishVerificationState.class),
                buffer.readEnum(WishFinalizationState.class), buffer.readEnum(WishAgentFallbackReason.class),
                buffer.readVarLong()));
    }
    public static void handle(WishAgentDebugPacket packet, Supplier<NetworkEvent.Context> context) {
        ServerPlayer sender = context.get().getSender();
        if (sender != null) WishAgentDebugStore.put(sender.getUUID(), packet.snapshot);
    }
}
