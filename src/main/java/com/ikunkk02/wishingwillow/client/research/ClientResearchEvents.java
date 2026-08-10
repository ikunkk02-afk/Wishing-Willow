package com.ikunkk02.wishingwillow.client.research;

import com.ikunkk02.wishingwillow.WishingWillow;
import com.ikunkk02.wishingwillow.research.ModResearchManager;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = WishingWillow.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientResearchEvents {
    private ClientResearchEvents() {
    }

    @SubscribeEvent
    public static void onLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        ModResearchManager.getInstance().onWorldJoin(event.getPlayer());
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ModResearchManager.getInstance().onWorldLogout();
    }
}
