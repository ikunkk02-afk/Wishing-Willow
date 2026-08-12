package com.ikunkk02.wishingwillow.client.ai;

import com.ikunkk02.wishingwillow.WishingWillow;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Thin {@code @EventBusSubscriber} that Forge loads early during automatic subscriber
 * registration.  This class deliberately imports <em>no</em> LangChain4j types so that
 * Forge can register the handler even when the shaded LangChain4j JARs have not yet
 * been added to the runtime classpath.
 *
 * <p>All actual AI logic lives in {@link ClientWishPlanningCoordinator}, which is only
 * loaded lazily when a wish-planning request arrives.</p>
 */
@Mod.EventBusSubscriber(modid = WishingWillow.MOD_ID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientWishPlanningEvents {

    private ClientWishPlanningEvents() { }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientWishPlanningCoordinator.onLogout();
    }
}
