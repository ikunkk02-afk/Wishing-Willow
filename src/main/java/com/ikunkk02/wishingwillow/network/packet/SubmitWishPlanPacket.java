package com.ikunkk02.wishingwillow.network.packet;

import com.ikunkk02.wishingwillow.network.PlanningPacketCodec;
import com.ikunkk02.wishingwillow.planning.CapabilityCatalog;
import com.ikunkk02.wishingwillow.planning.WishPlanError;
import com.ikunkk02.wishingwillow.planning.WishPlanValidator;
import com.ikunkk02.wishingwillow.wish.WishManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import javax.annotation.Nullable;
import java.util.UUID;
import java.util.function.Supplier;

public record SubmitWishPlanPacket(UUID sessionId,UUID attemptId,WishPlanError error,
                                   @Nullable CapabilityCatalog catalog,@Nullable String draftJson) {
    public static void encode(SubmitWishPlanPacket p,FriendlyByteBuf b){b.writeUUID(p.sessionId);b.writeUUID(p.attemptId);b.writeEnum(p.error);b.writeBoolean(p.catalog!=null&&p.draftJson!=null);if(p.catalog!=null&&p.draftJson!=null){PlanningPacketCodec.writeCatalog(b,p.catalog);b.writeUtf(p.draftJson,WishPlanValidator.MAX_AI_JSON);}}
    public static SubmitWishPlanPacket decode(FriendlyByteBuf b){UUID session=b.readUUID(),attempt=b.readUUID();WishPlanError error=b.readEnum(WishPlanError.class);if(!b.readBoolean())return new SubmitWishPlanPacket(session,attempt,error,null,null);return new SubmitWishPlanPacket(session,attempt,error,PlanningPacketCodec.readCatalog(b),b.readUtf(WishPlanValidator.MAX_AI_JSON));}
    public static void handle(SubmitWishPlanPacket p,Supplier<NetworkEvent.Context> c){ServerPlayer sender=c.get().getSender();if(sender!=null)WishManager.handlePlanSubmission(sender,p.sessionId,p.attemptId,p.error,p.catalog,p.draftJson);}
}
