package com.ikunkk02.wishingwillow.network.packet;

import com.ikunkk02.wishingwillow.network.ModNetworking;
import com.ikunkk02.wishingwillow.registry.ModItems;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraftforge.network.NetworkEvent;
import com.ikunkk02.wishingwillow.music.TradeRevealMusicState;

import java.util.function.Supplier;

/** Empty request; the server derives and verifies every fact from the player's currently open menu. */
public record TradeRevealDiscoveryPacket() {
    public static void encode(TradeRevealDiscoveryPacket packet, FriendlyByteBuf buffer) {}
    public static TradeRevealDiscoveryPacket decode(FriendlyByteBuf buffer) { return new TradeRevealDiscoveryPacket(); }
    public static void handle(TradeRevealDiscoveryPacket packet, Supplier<NetworkEvent.Context> context) {
        ServerPlayer player = context.get().getSender();
        if (player == null || !(player.containerMenu instanceof MerchantMenu menu)) return;
        boolean verified = menu.getOffers().stream().anyMatch(offer -> offer.getResult().is(ModItems.PACKAGED_WISHING_WILLOW.get()));
        if (!verified) return;
        var persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        boolean first = TradeRevealMusicState.markFirstDiscovery(persisted);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
        ModNetworking.sendToPlayer(player, new TradeRevealMusicPacket(first));
    }
}
