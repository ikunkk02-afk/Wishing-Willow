package com.ikunkk02.wishingwillow.network.packet;

import com.ikunkk02.wishingwillow.client.network.ClientPacketHandlers;
import com.ikunkk02.wishingwillow.execution.ExecutionSettingsSnapshot;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public record ExecutionSettingsPacket(ExecutionSettingsSnapshot settings) {
    public static void encode(ExecutionSettingsPacket p,FriendlyByteBuf b){var s=p.settings;b.writeBoolean(s.enabled());b.writeBoolean(s.thirdPartyEntities());b.writeBoolean(s.blockModification());b.writeBoolean(s.explosions());b.writeBoolean(s.destructiveExplosions());b.writeBoolean(s.crossDimensionTeleport());b.writeBoolean(s.debugSafeMode());b.writeVarInt(s.maximumDestructiveSeverity());b.writeBoolean(s.canEdit());}
    public static ExecutionSettingsPacket decode(FriendlyByteBuf b){return new ExecutionSettingsPacket(new ExecutionSettingsSnapshot(b.readBoolean(),b.readBoolean(),b.readBoolean(),b.readBoolean(),b.readBoolean(),b.readBoolean(),b.readBoolean(),b.readVarInt(),b.readBoolean()));}
    public static void handle(ExecutionSettingsPacket p,Supplier<NetworkEvent.Context> context){ClientPacketHandlers.executionSettings(p.settings);}
}
