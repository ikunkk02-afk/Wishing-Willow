package com.ikunkk02.wishingwillow.network.packet;

import com.ikunkk02.wishingwillow.client.music.TradeRevealMusicTracker;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record TradeRevealMusicPacket(boolean firstDiscovery) {
    public static void encode(TradeRevealMusicPacket packet, FriendlyByteBuf buffer) { buffer.writeBoolean(packet.firstDiscovery); }
    public static TradeRevealMusicPacket decode(FriendlyByteBuf buffer) { return new TradeRevealMusicPacket(buffer.readBoolean()); }
    public static void handle(TradeRevealMusicPacket packet, Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> TradeRevealMusicTracker.resolved(packet.firstDiscovery));
    }
}
