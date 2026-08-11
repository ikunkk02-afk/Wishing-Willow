package com.ikunkk02.wishingwillow.client.music;

import com.ikunkk02.wishingwillow.WishingWillow;
import com.ikunkk02.wishingwillow.client.cinematic.WishingWillowCinematicFilterController;
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

@Mod.EventBusSubscriber(modid = WishingWillow.MOD_ID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TradeRevealExperienceTracker {
    private static final TradeRevealExperienceState STATE = new TradeRevealExperienceState();
    private static boolean requestSent;
    private static boolean discoveryResolved;
    private static MerchantScreen activeScreen;

    private TradeRevealExperienceTracker() {
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.screen instanceof MerchantScreen screen)) {
            handle(STATE.observe(false, false));
            activeScreen = null;
            return;
        }
        if (activeScreen != screen) {
            handle(STATE.observe(false, false));
            activeScreen = screen;
        }
        STATE.observe(true, false);
        if (!STATE.shouldScanOffers()) return;
        boolean containsWillow = screen.getMenu().getOffers().stream()
                .anyMatch(offer -> offer.getResult().is(ModItems.PACKAGED_WISHING_WILLOW.get()));
        handle(STATE.observe(true, containsWillow));
    }

    private static void handle(TradeRevealExperienceState.Update update) {
        if (update == TradeRevealExperienceState.Update.REVEALED) {
            WishingWillowCinematicFilterController.startTradeReveal();
            if (!discoveryResolved && !requestSent) {
                requestSent = true;
                ModNetworking.sendToServer(new TradeRevealDiscoveryPacket());
            }
        } else if (update == TradeRevealExperienceState.Update.CLOSED_REVEAL) {
            WishingWillowCinematicFilterController.endTradeReveal();
            WishingWillowMusicController.tradeScreenClosed();
        }
    }

    public static void resolved(boolean firstDiscovery) {
        discoveryResolved = true;
        requestSent = false;
        if (firstDiscovery) WishingWillowMusicController.startTradeReveal();
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        requestSent = discoveryResolved = false;
        activeScreen = null;
        STATE.clear();
        WishingWillowCinematicFilterController.clear();
    }
}
