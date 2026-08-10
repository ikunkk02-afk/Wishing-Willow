package com.ikunkk02.wishingwillow.network.packet;

import com.ikunkk02.wishingwillow.planning.WishPlanState;
import com.ikunkk02.wishingwillow.wish.WishManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public record WishPlanningProgressPacket(UUID sessionId,UUID attemptId,WishPlanState state) {
    public static void encode(WishPlanningProgressPacket p,FriendlyByteBuf b){b.writeUUID(p.sessionId);b.writeUUID(p.attemptId);b.writeEnum(p.state);}
    public static WishPlanningProgressPacket decode(FriendlyByteBuf b){return new WishPlanningProgressPacket(b.readUUID(),b.readUUID(),b.readEnum(WishPlanState.class));}
    public static void handle(WishPlanningProgressPacket p,Supplier<NetworkEvent.Context> c){ServerPlayer sender=c.get().getSender();if(sender!=null)WishManager.handlePlanningProgress(sender,p.sessionId,p.attemptId,p.state);}
}
