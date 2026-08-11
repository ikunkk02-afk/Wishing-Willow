package com.ikunkk02.wishingwillow.network.packet;

import com.ikunkk02.wishingwillow.network.PlanningPacketCodec;
import com.ikunkk02.wishingwillow.planning.CapabilityCatalog;
import com.ikunkk02.wishingwillow.planning.WishPlanError;
import com.ikunkk02.wishingwillow.planning.WishPlanValidator;
import com.ikunkk02.wishingwillow.planning.WishPlanResult;
import com.ikunkk02.wishingwillow.planning.WishPlanJson;
import com.ikunkk02.wishingwillow.wish.WishManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import javax.annotation.Nullable;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * @deprecated Legacy WishPlan compatibility only. Do not use for WishProgram execution.
 */
@Deprecated
public record SubmitWishPlanPacket(UUID sessionId,UUID attemptId,WishPlanError error,int attemptsUsed,
                                   @Nullable CapabilityCatalog catalog,@Nullable String draftJson) {
    public SubmitWishPlanPacket {
        if (attemptsUsed < 1 || attemptsUsed > 3) throw new IllegalArgumentException("INVALID_ATTEMPT_COUNT");
    }
    public static SubmitWishPlanPacket fromResult(UUID sessionId, UUID attemptId,
                                                  WishPlanResult result,
                                                  @Nullable CapabilityCatalog catalog) {
        if (result.draft() == null || catalog == null) {
            WishPlanError error = result.error() == WishPlanError.NONE ? WishPlanError.UNKNOWN : result.error();
            return new SubmitWishPlanPacket(sessionId, attemptId, error, result.attemptsUsed(), null, null);
        }
        return new SubmitWishPlanPacket(sessionId, attemptId, WishPlanError.NONE, result.attemptsUsed(),
                catalog, WishPlanJson.toAiJson(result.draft()));
    }
    public static void encode(SubmitWishPlanPacket p,FriendlyByteBuf b){b.writeUUID(p.sessionId);b.writeUUID(p.attemptId);b.writeEnum(p.error);b.writeByte(p.attemptsUsed);b.writeBoolean(p.catalog!=null&&p.draftJson!=null);if(p.catalog!=null&&p.draftJson!=null){PlanningPacketCodec.writeCatalog(b,p.catalog);b.writeUtf(p.draftJson,WishPlanValidator.MAX_AI_JSON);}}
    public static SubmitWishPlanPacket decode(FriendlyByteBuf b){UUID session=b.readUUID(),attempt=b.readUUID();WishPlanError error=b.readEnum(WishPlanError.class);int attemptsUsed=b.readUnsignedByte();if(!b.readBoolean())return new SubmitWishPlanPacket(session,attempt,error,attemptsUsed,null,null);return new SubmitWishPlanPacket(session,attempt,error,attemptsUsed,PlanningPacketCodec.readCatalog(b),b.readUtf(WishPlanValidator.MAX_AI_JSON));}
    public static void handle(SubmitWishPlanPacket p,Supplier<NetworkEvent.Context> c){ServerPlayer sender=c.get().getSender();if(sender!=null)WishManager.handlePlanSubmission(sender,p.sessionId,p.attemptId,p.error,p.attemptsUsed,p.catalog,p.draftJson);}
}
