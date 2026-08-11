package com.ikunkk02.wishingwillow.client.cinematic;

import com.ikunkk02.wishingwillow.WishingWillow;
import com.ikunkk02.wishingwillow.config.WishingWillowClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.GameShuttingDownEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = WishingWillow.MOD_ID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WishingWillowCinematicFilterController {
    private static final CinematicFilterTimeline TIMELINE = new CinematicFilterTimeline();
    private static long renderFrame;

    private WishingWillowCinematicFilterController() {
    }

    public static void startTradeReveal() {
        if (!WishingWillowClientConfig.CINEMATIC_TRADE_FILTER.get()) {
            clear();
            return;
        }
        TIMELINE.start();
    }

    public static void endTradeReveal() {
        TIMELINE.finish();
    }

    public static CinematicFilterState state() {
        return TIMELINE.state();
    }

    public static float alpha(float partialTick) {
        return TIMELINE.alpha(partialTick);
    }

    static long nextRenderFrame() {
        return ++renderFrame;
    }

    public static void clear() {
        TIMELINE.clear();
        renderFrame = 0;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (!WishingWillowClientConfig.CINEMATIC_TRADE_FILTER.get()
                || minecraft.player == null || minecraft.level == null) {
            clear();
            return;
        }
        TIMELINE.tick();
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }

    public static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) resourceManager -> clear());
    }

    @SubscribeEvent
    public static void onGameShuttingDown(GameShuttingDownEvent event) {
        clear();
    }
}
