package com.ikunkk02.wishingwillow.network;

import com.ikunkk02.wishingwillow.WishingWillow;
import com.ikunkk02.wishingwillow.network.packet.OpenWishScreenPacket;
import com.ikunkk02.wishingwillow.network.packet.SubmitWishPacket;
import com.ikunkk02.wishingwillow.network.packet.SubmitWishInterpretationPacket;
import com.ikunkk02.wishingwillow.network.packet.WishAnimationEventPacket;
import com.ikunkk02.wishingwillow.network.packet.WishStartedPacket;
import com.ikunkk02.wishingwillow.network.packet.WishStatePacket;
import com.ikunkk02.wishingwillow.network.packet.WishPipelineStatePacket;
import com.ikunkk02.wishingwillow.network.packet.WishPlanningRequestPacket;
import com.ikunkk02.wishingwillow.network.packet.WishPlanningProgressPacket;
import com.ikunkk02.wishingwillow.network.packet.SubmitWishPlanPacket;
import com.ikunkk02.wishingwillow.network.packet.RequestExecutionSettingsPacket;
import com.ikunkk02.wishingwillow.network.packet.ExecutionSettingsPacket;
import com.ikunkk02.wishingwillow.network.packet.UpdateExecutionSettingsPacket;
import com.ikunkk02.wishingwillow.network.packet.UnboxingStartedPacket;
import com.ikunkk02.wishingwillow.network.packet.UnboxingStatePacket;
import com.ikunkk02.wishingwillow.network.packet.WishOmenPacket;
import com.ikunkk02.wishingwillow.network.packet.TradeRevealDiscoveryPacket;
import com.ikunkk02.wishingwillow.network.packet.TradeRevealMusicPacket;
import com.ikunkk02.wishingwillow.network.packet.WishAgentDebugPacket;
import com.ikunkk02.wishingwillow.network.packet.CancelWishPlanningPacket;
import com.ikunkk02.wishingwillow.network.packet.SubmitWishProgramPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ModNetworking {
    private static final String PROTOCOL_VERSION = "14";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(WishingWillow.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private ModNetworking() {
    }

    public static void register() {
        int id = 0;
        CHANNEL.messageBuilder(OpenWishScreenPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(OpenWishScreenPacket::encode)
                .decoder(OpenWishScreenPacket::decode)
                .consumerMainThread(OpenWishScreenPacket::handle)
                .add();
        CHANNEL.messageBuilder(SubmitWishPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SubmitWishPacket::encode)
                .decoder(SubmitWishPacket::decode)
                .consumerMainThread(SubmitWishPacket::handle)
                .add();
        CHANNEL.messageBuilder(WishStartedPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(WishStartedPacket::encode)
                .decoder(WishStartedPacket::decode)
                .consumerMainThread(WishStartedPacket::handle)
                .add();
        CHANNEL.messageBuilder(WishAnimationEventPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(WishAnimationEventPacket::encode)
                .decoder(WishAnimationEventPacket::decode)
                .consumerMainThread(WishAnimationEventPacket::handle)
                .add();
        CHANNEL.messageBuilder(WishStatePacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(WishStatePacket::encode)
                .decoder(WishStatePacket::decode)
                .consumerMainThread(WishStatePacket::handle)
                .add();
        CHANNEL.messageBuilder(WishPipelineStatePacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(WishPipelineStatePacket::encode)
                .decoder(WishPipelineStatePacket::decode)
                .consumerMainThread(WishPipelineStatePacket::handle)
                .add();
        CHANNEL.messageBuilder(SubmitWishInterpretationPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SubmitWishInterpretationPacket::encode)
                .decoder(SubmitWishInterpretationPacket::decode)
                .consumerMainThread(SubmitWishInterpretationPacket::handle)
                .add();
        CHANNEL.messageBuilder(WishPlanningRequestPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(WishPlanningRequestPacket::encode).decoder(WishPlanningRequestPacket::decode)
                .consumerMainThread(WishPlanningRequestPacket::handle).add();
        CHANNEL.messageBuilder(WishPlanningProgressPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(WishPlanningProgressPacket::encode).decoder(WishPlanningProgressPacket::decode)
                .consumerMainThread(WishPlanningProgressPacket::handle).add();
        CHANNEL.messageBuilder(SubmitWishPlanPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SubmitWishPlanPacket::encode).decoder(SubmitWishPlanPacket::decode)
                .consumerMainThread(SubmitWishPlanPacket::handle).add();
        CHANNEL.messageBuilder(RequestExecutionSettingsPacket.class,id++,NetworkDirection.PLAY_TO_SERVER)
                .encoder(RequestExecutionSettingsPacket::encode).decoder(RequestExecutionSettingsPacket::decode)
                .consumerMainThread(RequestExecutionSettingsPacket::handle).add();
        CHANNEL.messageBuilder(ExecutionSettingsPacket.class,id++,NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ExecutionSettingsPacket::encode).decoder(ExecutionSettingsPacket::decode)
                .consumerMainThread(ExecutionSettingsPacket::handle).add();
        CHANNEL.messageBuilder(UpdateExecutionSettingsPacket.class,id++,NetworkDirection.PLAY_TO_SERVER)
                .encoder(UpdateExecutionSettingsPacket::encode).decoder(UpdateExecutionSettingsPacket::decode)
                .consumerMainThread(UpdateExecutionSettingsPacket::handle).add();
        CHANNEL.messageBuilder(UnboxingStartedPacket.class,id++,NetworkDirection.PLAY_TO_CLIENT)
                .encoder(UnboxingStartedPacket::encode).decoder(UnboxingStartedPacket::decode)
                .consumerMainThread(UnboxingStartedPacket::handle).add();
        CHANNEL.messageBuilder(UnboxingStatePacket.class,id++,NetworkDirection.PLAY_TO_CLIENT)
                .encoder(UnboxingStatePacket::encode).decoder(UnboxingStatePacket::decode)
                .consumerMainThread(UnboxingStatePacket::handle).add();
        CHANNEL.messageBuilder(WishOmenPacket.class,id++,NetworkDirection.PLAY_TO_CLIENT)
                .encoder(WishOmenPacket::encode).decoder(WishOmenPacket::decode)
                .consumerMainThread(WishOmenPacket::handle).add();
        CHANNEL.messageBuilder(TradeRevealDiscoveryPacket.class,id++,NetworkDirection.PLAY_TO_SERVER)
                .encoder(TradeRevealDiscoveryPacket::encode).decoder(TradeRevealDiscoveryPacket::decode)
                .consumerMainThread(TradeRevealDiscoveryPacket::handle).add();
        CHANNEL.messageBuilder(TradeRevealMusicPacket.class,id++,NetworkDirection.PLAY_TO_CLIENT)
                .encoder(TradeRevealMusicPacket::encode).decoder(TradeRevealMusicPacket::decode)
                .consumerMainThread(TradeRevealMusicPacket::handle).add();
        CHANNEL.messageBuilder(CancelWishPlanningPacket.class,id++,NetworkDirection.PLAY_TO_SERVER)
                .encoder(CancelWishPlanningPacket::encode).decoder(CancelWishPlanningPacket::decode)
                .consumerMainThread(CancelWishPlanningPacket::handle).add();
        CHANNEL.messageBuilder(SubmitWishProgramPacket.class,id++,NetworkDirection.PLAY_TO_SERVER)
                .encoder(SubmitWishProgramPacket::encode).decoder(SubmitWishProgramPacket::decode)
                .consumerMainThread(SubmitWishProgramPacket::handle).add();
        CHANNEL.messageBuilder(WishAgentDebugPacket.class,id,NetworkDirection.PLAY_TO_SERVER)
                .encoder(WishAgentDebugPacket::encode).decoder(WishAgentDebugPacket::decode)
                .consumerMainThread(WishAgentDebugPacket::handle).add();
    }

    public static void sendToPlayer(ServerPlayer player, Object packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    public static void sendToServer(Object packet) {
        CHANNEL.sendToServer(packet);
    }
}
