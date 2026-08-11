package com.ikunkk02.wishingwillow.client.music;

import com.ikunkk02.wishingwillow.WishingWillow;
import com.ikunkk02.wishingwillow.network.ModNetworking;
import com.ikunkk02.wishingwillow.network.packet.TradeRevealDiscoveryPacket;
import com.ikunkk02.wishingwillow.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = WishingWillow.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TradeRevealMusicTracker {
    private static boolean requestSent;
    private static boolean discoveryResolved;
    private static boolean merchantOpen;
    private TradeRevealMusicTracker() {}

    @SubscribeEvent public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof MerchantScreen screen) {
            merchantOpen = true;
            if (!discoveryResolved && !requestSent && screen.getMenu().getOffers().stream()
                    .anyMatch(offer -> offer.getResult().is(ModItems.PACKAGED_WISHING_WILLOW.get()))) {
                requestSent = true;
                ModNetworking.sendToServer(new TradeRevealDiscoveryPacket());
            }
        } else if (merchantOpen) {
            merchantOpen = false;
            WishingWillowMusicController.tradeScreenClosed();
        }
    }

    public static void resolved(boolean firstDiscovery) {
        discoveryResolved = true;
        requestSent = false;
        if (firstDiscovery) WishingWillowMusicController.startTradeReveal();
    }

    @SubscribeEvent public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        requestSent = discoveryResolved = merchantOpen = false;
    }
}
