package com.ikunkk02.wishingwillow.network.packet;

import com.ikunkk02.wishingwillow.execution.ExecutionSettingsSnapshot;
import com.ikunkk02.wishingwillow.execution.WishExecutionConfig;
import com.ikunkk02.wishingwillow.network.ModNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public record UpdateExecutionSettingsPacket(ExecutionSettingsSnapshot settings) {
    public static void encode(UpdateExecutionSettingsPacket p,FriendlyByteBuf b){ExecutionSettingsPacket.encode(new ExecutionSettingsPacket(p.settings),b);}
    public static UpdateExecutionSettingsPacket decode(FriendlyByteBuf b){return new UpdateExecutionSettingsPacket(ExecutionSettingsPacket.decode(b).settings());}
    public static void handle(UpdateExecutionSettingsPacket p,Supplier<NetworkEvent.Context> context){ServerPlayer sender=context.get().getSender();if(sender==null||!sender.hasPermissions(2))return;var s=p.settings;WishExecutionConfig.ENABLED.set(s.enabled());WishExecutionConfig.THIRD_PARTY_ENTITIES.set(s.thirdPartyEntities());WishExecutionConfig.BLOCK_MODIFICATION.set(s.blockModification());WishExecutionConfig.EXPLOSIONS.set(s.explosions());WishExecutionConfig.DESTRUCTIVE_EXPLOSIONS.set(s.destructiveExplosions());WishExecutionConfig.CROSS_DIMENSION_TELEPORT.set(s.crossDimensionTeleport());WishExecutionConfig.DEBUG_SAFE_MODE.set(s.debugSafeMode());WishExecutionConfig.MAX_DESTRUCTIVE_SEVERITY.set(Math.max(0,Math.min(100,s.maximumDestructiveSeverity())));WishExecutionConfig.SPEC.save();ModNetworking.sendToPlayer(sender,new ExecutionSettingsPacket(ExecutionSettingsSnapshot.current(true)));}
}
