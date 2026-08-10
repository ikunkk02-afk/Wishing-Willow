package com.ikunkk02.wishingwillow.network.packet;

import com.ikunkk02.wishingwillow.execution.ExecutionSettingsSnapshot;
import com.ikunkk02.wishingwillow.network.ModNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public record RequestExecutionSettingsPacket() {
    public static void encode(RequestExecutionSettingsPacket p,FriendlyByteBuf b){}
    public static RequestExecutionSettingsPacket decode(FriendlyByteBuf b){return new RequestExecutionSettingsPacket();}
    public static void handle(RequestExecutionSettingsPacket p,Supplier<NetworkEvent.Context> context){ServerPlayer sender=context.get().getSender();if(sender!=null)ModNetworking.sendToPlayer(sender,new ExecutionSettingsPacket(ExecutionSettingsSnapshot.current(sender.hasPermissions(2))));}
}
