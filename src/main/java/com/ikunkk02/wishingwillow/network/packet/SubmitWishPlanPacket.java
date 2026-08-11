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

public record SubmitWishPlanPacket(UUID sessionId,UUID attemptId,WishPlanError error,int attemptsUsed,
                                   @Nullable CapabilityCatalog catalog,@Nullable String draftJson) {
    public SubmitWishPlanPacket {
        if (attemptsUsed < 1 || attemptsUsed > 3) throw new IllegalArgumentException("INVALID_ATTEMPT_COUNT");
    }
    public static void encode(SubmitWishPlanPacket p,FriendlyByteBuf b){b.writeUUID(p.sessionId);b.writeUUID(p.attemptId);b.writeEnum(p.error);b.writeByte(p.attemptsUsed);b.writeBoolean(p.catalog!=null&&p.draftJson!=null);if(p.catalog!=null&&p.draftJson!=null){PlanningPacketCodec.writeCatalog(b,p.catalog);b.writeUtf(p.draftJson,WishPlanValidator.MAX_AI_JSON);}}
    public static SubmitWishPlanPacket decode(FriendlyByteBuf b){UUID session=b.readUUID(),attempt=b.readUUID();WishPlanError error=b.readEnum(WishPlanError.class);int attemptsUsed=b.readUnsignedByte();if(!b.readBoolean())return new SubmitWishPlanPacket(session,attempt,error,attemptsUsed,null,null);return new SubmitWishPlanPacket(session,attempt,error,attemptsUsed,PlanningPacketCodec.readCatalog(b),b.readUtf(WishPlanValidator.MAX_AI_JSON));}
    public static void handle(SubmitWishPlanPacket p,Supplier<NetworkEvent.Context> c){ServerPlayer sender=c.get().getSender();if(sender!=null)WishManager.handlePlanSubmission(sender,p.sessionId,p.attemptId,p.error,p.attemptsUsed,p.catalog,p.draftJson);}
}
