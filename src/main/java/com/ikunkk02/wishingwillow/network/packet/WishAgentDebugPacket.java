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
        buffer.writeEnum(value.route()); buffer.writeUtf(value.routeReason(), 160);
        buffer.writeUtf(value.coreOutcome(), 512); buffer.writeEnum(value.absurdityStyle());
        buffer.writeVarInt(value.absurdityIntensity()); buffer.writeVarInt(value.directActions().size());
        value.directActions().forEach(action -> buffer.writeUtf(action, 64));
    }
    public static WishAgentDebugPacket decode(FriendlyByteBuf buffer) {
        UUID session = buffer.readUUID(); WishPlanningMode mode = buffer.readEnum(WishPlanningMode.class);
        WishAgentDebugState state = buffer.readEnum(WishAgentDebugState.class);
        int iterations = buffer.readVarInt(); int calls = buffer.readVarInt();
        int count = buffer.readVarInt(); if (count < 0 || count > 48) throw new IllegalArgumentException("INVALID_DEBUG_PACKET");
        List<String> tools = new ArrayList<>(); for (int i = 0; i < count; i++) tools.add(buffer.readUtf(64));
        String lastTool = buffer.readUtf(64); String lastStatus = buffer.readUtf(96);
        WishVerificationState verification = buffer.readEnum(WishVerificationState.class);
        WishFinalizationState finalization = buffer.readEnum(WishFinalizationState.class);
        WishAgentFallbackReason fallback = buffer.readEnum(WishAgentFallbackReason.class);
        long elapsed = buffer.readVarLong();
        com.ikunkk02.wishingwillow.planning.WishExecutionRoute route =
                buffer.readEnum(com.ikunkk02.wishingwillow.planning.WishExecutionRoute.class);
        String routeReason = buffer.readUtf(160); String coreOutcome = buffer.readUtf(512);
        com.ikunkk02.wishingwillow.planning.direct.WishAbsurdityStyle absurdity =
                buffer.readEnum(com.ikunkk02.wishingwillow.planning.direct.WishAbsurdityStyle.class);
        int intensity = buffer.readVarInt(); int actionCount = buffer.readVarInt();
        if (actionCount < 0 || actionCount > 16) throw new IllegalArgumentException("INVALID_DEBUG_PACKET");
        List<String> directActions = new ArrayList<>();
        for (int i = 0; i < actionCount; i++) directActions.add(buffer.readUtf(64));
        return new WishAgentDebugPacket(new WishAgentDebugSnapshot(session, mode, state, iterations, calls, tools,
                lastTool, lastStatus, verification, finalization, fallback, elapsed, route, routeReason,
                coreOutcome, absurdity, intensity, directActions));
    }
    public static void handle(WishAgentDebugPacket packet, Supplier<NetworkEvent.Context> context) {
        ServerPlayer sender = context.get().getSender();
        if (sender != null) WishAgentDebugStore.put(sender.getUUID(), packet.snapshot);
    }
}
