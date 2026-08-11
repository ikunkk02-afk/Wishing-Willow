package com.ikunkk02.wishingwillow.client.cinematic;

import com.ikunkk02.wishingwillow.WishingWillow;
import com.ikunkk02.wishingwillow.config.WishingWillowClientConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = WishingWillow.MOD_ID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TradeRevealCinematicOverlay {
    private static final int VIGNETTE_BANDS = 10;

    private TradeRevealCinematicOverlay() {
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        float alpha = WishingWillowCinematicFilterController.alpha(event.getPartialTick());
        if (alpha <= 0.001F || !WishingWillowClientConfig.CINEMATIC_TRADE_FILTER.get()) return;
        render(event.getGuiGraphics(), alpha, WishingWillowClientConfig.CINEMATIC_FILTER_INTENSITY.get(),
                WishingWillowCinematicFilterController.nextRenderFrame());
    }

    static void render(GuiGraphics graphics, float alpha, CinematicFilterIntensity intensity,
                       long frame) {
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        float scale = intensity.scale();
        float tint = Math.min(0.20F, 0.16F * scale) * alpha;
        float exposure = Math.min(0.10F, 0.07F * scale) * alpha;
        float vignette = Math.min(0.22F, 0.16F * scale) * alpha;
        float grain = Math.min(0.022F, 0.018F * scale) * alpha;

        graphics.fill(0, 0, width, height, argb(tint, 211, 208, 199));
        graphics.fill(0, 0, width, height, argb(exposure, 8, 14, 17));
        graphics.fillGradient(0, 0, width, Math.max(1, height / 3),
                argb(0.035F * alpha, 255, 242, 204), 0x00FFF2CC);
        renderVignette(graphics, width, height, vignette);
        renderGrain(graphics, width, height, grain, frame);
    }

    private static void renderVignette(GuiGraphics graphics, int width, int height, float strength) {
        int edge = Math.max(12, Math.min(width, height) / 6);
        for (int band = 0; band < VIGNETTE_BANDS; band++) {
            float outer = (VIGNETTE_BANDS - band) / (float) VIGNETTE_BANDS;
            int color = argb(strength * outer * outer, 8, 24, 27);
            int start = band * edge / VIGNETTE_BANDS;
            int end = (band + 1) * edge / VIGNETTE_BANDS;
            graphics.fill(0, start, width, end, color);
            graphics.fill(0, height - end, width, height - start, color);
            graphics.fill(start, edge, end, height - edge, color);
            graphics.fill(width - end, edge, width - start, height - edge, color);
        }
    }

    private static void renderGrain(GuiGraphics graphics, int width, int height, float strength,
                                    long frame) {
        int count = Math.min(180, Math.max(24, width * height / 12000));
        long random = frame * 0x9E3779B97F4A7C15L + width * 31L + height;
        for (int index = 0; index < count; index++) {
            random ^= random << 13;
            random ^= random >>> 7;
            random ^= random << 17;
            int x = Math.floorMod((int) random, Math.max(1, width));
            int y = Math.floorMod((int) (random >>> 32), Math.max(1, height));
            boolean light = (random & 1L) == 0;
            int color = light ? argb(strength * 0.65F, 236, 228, 209)
                    : argb(strength, 12, 22, 24);
            graphics.fill(x, y, x + 1, y + 1, color);
        }
    }

    private static int argb(float alpha, int red, int green, int blue) {
        int a = Math.max(0, Math.min(255, Math.round(alpha * 255.0F)));
        return a << 24 | red << 16 | green << 8 | blue;
    }
}
