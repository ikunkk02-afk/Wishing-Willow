package com.ikunkk02.wishingwillow.network.packet;

import com.ikunkk02.wishingwillow.ai.AiConfig;
import com.ikunkk02.wishingwillow.ai.AiProviderType;
import com.ikunkk02.wishingwillow.ai.WishInterpretation;
import com.ikunkk02.wishingwillow.client.network.ClientPacketHandlers;
import com.ikunkk02.wishingwillow.network.PlanningPacketCodec;
import com.ikunkk02.wishingwillow.planning.WishContextSnapshot;
import com.ikunkk02.wishingwillow.wish.WishTextValidator;
import com.ikunkk02.wishingwillow.execution.ExecutionSettingsSnapshot;
import com.ikunkk02.wishingwillow.program.WishProgram;
import com.ikunkk02.wishingwillow.program.WishProgramJson;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public record WishPlanningRequestPacket(UUID sessionId, UUID attemptId, String originalWish,
                                        AiProviderType providerType, String model,
                                        WishInterpretation interpretation, WishProgram program, WishContextSnapshot context,
                                        ExecutionSettingsSnapshot executionSettings) {
    public WishPlanningRequestPacket(UUID sessionId, UUID attemptId, String originalWish,
                                     AiProviderType providerType, String model, WishInterpretation interpretation,
                                     WishContextSnapshot context, ExecutionSettingsSnapshot executionSettings) {
        this(sessionId, attemptId, originalWish, providerType, model, interpretation, null, context, executionSettings);
    }
    public static void encode(WishPlanningRequestPacket p,FriendlyByteBuf b){b.writeUUID(p.sessionId);b.writeUUID(p.attemptId);b.writeUtf(p.originalWish,WishTextValidator.MAX_LENGTH);b.writeEnum(p.providerType);b.writeUtf(p.model,AiConfig.MAX_MODEL_LENGTH);PlanningPacketCodec.writeInterpretation(b,p.interpretation);b.writeBoolean(p.program!=null);if(p.program!=null)b.writeUtf(WishProgramJson.toJson(p.program),WishProgramJson.MAX_JSON);PlanningPacketCodec.writeContext(b,p.context);PlanningPacketCodec.writeExecutionSettings(b,p.executionSettings);}
    public static WishPlanningRequestPacket decode(FriendlyByteBuf b){UUID session=b.readUUID(),attempt=b.readUUID();String wish=b.readUtf(WishTextValidator.MAX_LENGTH);AiProviderType provider=b.readEnum(AiProviderType.class);String model=b.readUtf(AiConfig.MAX_MODEL_LENGTH);WishInterpretation interpretation=PlanningPacketCodec.readInterpretation(b);WishProgram program=b.readBoolean()?WishProgramJson.parseAndValidate(b.readUtf(WishProgramJson.MAX_JSON)):null;return new WishPlanningRequestPacket(session,attempt,wish,provider,model,interpretation,program,PlanningPacketCodec.readContext(b),PlanningPacketCodec.readExecutionSettings(b));}
    public static void handle(WishPlanningRequestPacket p,Supplier<NetworkEvent.Context> c){DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->ClientPacketHandlers.startPlanning(p));}
}
