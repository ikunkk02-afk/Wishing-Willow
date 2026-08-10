package com.ikunkk02.wishingwillow.network.packet;

import com.ikunkk02.wishingwillow.ai.AiConfig;
import com.ikunkk02.wishingwillow.ai.AiProviderType;
import com.ikunkk02.wishingwillow.ai.WishInterpretation;
import com.ikunkk02.wishingwillow.client.network.ClientPacketHandlers;
import com.ikunkk02.wishingwillow.network.PlanningPacketCodec;
import com.ikunkk02.wishingwillow.planning.WishContextSnapshot;
import com.ikunkk02.wishingwillow.wish.WishTextValidator;
import com.ikunkk02.wishingwillow.execution.ExecutionSettingsSnapshot;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public record WishPlanningRequestPacket(UUID sessionId, UUID attemptId, String originalWish,
                                        AiProviderType providerType, String model,
                                        WishInterpretation interpretation, WishContextSnapshot context,
                                        ExecutionSettingsSnapshot executionSettings) {
    public static void encode(WishPlanningRequestPacket p,FriendlyByteBuf b){b.writeUUID(p.sessionId);b.writeUUID(p.attemptId);b.writeUtf(p.originalWish,WishTextValidator.MAX_LENGTH);b.writeEnum(p.providerType);b.writeUtf(p.model,AiConfig.MAX_MODEL_LENGTH);PlanningPacketCodec.writeInterpretation(b,p.interpretation);PlanningPacketCodec.writeContext(b,p.context);PlanningPacketCodec.writeExecutionSettings(b,p.executionSettings);}
    public static WishPlanningRequestPacket decode(FriendlyByteBuf b){return new WishPlanningRequestPacket(b.readUUID(),b.readUUID(),b.readUtf(WishTextValidator.MAX_LENGTH),b.readEnum(AiProviderType.class),b.readUtf(AiConfig.MAX_MODEL_LENGTH),PlanningPacketCodec.readInterpretation(b),PlanningPacketCodec.readContext(b),PlanningPacketCodec.readExecutionSettings(b));}
    public static void handle(WishPlanningRequestPacket p,Supplier<NetworkEvent.Context> c){DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->ClientPacketHandlers.startPlanning(p));}
}
