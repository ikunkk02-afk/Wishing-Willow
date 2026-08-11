package com.ikunkk02.wishingwillow.client.gui;

import com.ikunkk02.wishingwillow.ai.WishFulfillmentMode;
import com.ikunkk02.wishingwillow.client.music.WishingWillowMusicController;
import com.ikunkk02.wishingwillow.client.cinematic.CinematicFilterIntensity;
import com.ikunkk02.wishingwillow.client.cinematic.WishingWillowCinematicFilterController;
import com.ikunkk02.wishingwillow.config.WishingWillowClientConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Locale;

public final class MusicSettingsScreen extends Screen {
    private final Screen parent;
    public MusicSettingsScreen(Screen parent) {
        super(Component.translatable("screen.wishing_willow.music.title"));
        this.parent = parent;
    }

    @Override protected void init() {
        int width = Math.min(280, this.width - 24), x = (this.width - width) / 2,
                y = Math.max(20, this.height / 2 - 88);
        addRenderableWidget(RetroButton.create(modeLabel(), button -> {
            WishFulfillmentMode current = WishingWillowClientConfig.FULFILLMENT_MODE.get();
            WishFulfillmentMode[] values = WishFulfillmentMode.values();
            WishingWillowClientConfig.FULFILLMENT_MODE.set(values[(current.ordinal() + 1) % values.length]);
            save(); button.setMessage(modeLabel());
        }, x, y, width, 20));
        addRenderableWidget(RetroButton.create(masterLabel(), button -> {
            WishingWillowClientConfig.CINEMATIC_MUSIC.set(!WishingWillowClientConfig.CINEMATIC_MUSIC.get());
            if (!WishingWillowClientConfig.CINEMATIC_MUSIC.get()) WishingWillowMusicController.clear();
            save(); button.setMessage(masterLabel());
        }, x, y + 26, width, 20));
        addRenderableWidget(RetroButton.create(volumeLabel(), button -> {
            int next = (WishingWillowClientConfig.MUSIC_VOLUME.get() + 10) % 110;
            WishingWillowClientConfig.MUSIC_VOLUME.set(next); if (next == 0) WishingWillowMusicController.clear();
            save(); button.setMessage(volumeLabel());
        }, x, y + 52, width, 20));
        addRenderableWidget(RetroButton.create(toggleLabel("trade", WishingWillowClientConfig.TRADE_REVEAL_MUSIC.get()), button -> {
            WishingWillowClientConfig.TRADE_REVEAL_MUSIC.set(!WishingWillowClientConfig.TRADE_REVEAL_MUSIC.get());
            save(); button.setMessage(toggleLabel("trade", WishingWillowClientConfig.TRADE_REVEAL_MUSIC.get()));
        }, x, y + 78, width, 20));
        addRenderableWidget(RetroButton.create(toggleLabel("wish", WishingWillowClientConfig.WISH_SEQUENCE_MUSIC.get()), button -> {
            WishingWillowClientConfig.WISH_SEQUENCE_MUSIC.set(!WishingWillowClientConfig.WISH_SEQUENCE_MUSIC.get());
            if (!WishingWillowClientConfig.WISH_SEQUENCE_MUSIC.get()) WishingWillowMusicController.clear();
            save(); button.setMessage(toggleLabel("wish", WishingWillowClientConfig.WISH_SEQUENCE_MUSIC.get()));
        }, x, y + 104, width, 20));
        addRenderableWidget(RetroButton.create(toggleLabel("filter", WishingWillowClientConfig.CINEMATIC_TRADE_FILTER.get()), button -> {
            WishingWillowClientConfig.CINEMATIC_TRADE_FILTER.set(!WishingWillowClientConfig.CINEMATIC_TRADE_FILTER.get());
            if (!WishingWillowClientConfig.CINEMATIC_TRADE_FILTER.get()) {
                WishingWillowCinematicFilterController.clear();
            }
            save(); button.setMessage(toggleLabel("filter", WishingWillowClientConfig.CINEMATIC_TRADE_FILTER.get()));
        }, x, y + 130, width, 20));
        addRenderableWidget(RetroButton.create(intensityLabel(), button -> {
            CinematicFilterIntensity current = WishingWillowClientConfig.CINEMATIC_FILTER_INTENSITY.get();
            CinematicFilterIntensity[] values = CinematicFilterIntensity.values();
            WishingWillowClientConfig.CINEMATIC_FILTER_INTENSITY.set(
                    values[(current.ordinal() + 1) % values.length]);
            save(); button.setMessage(intensityLabel());
        }, x, y + 156, width, 20));
        addRenderableWidget(RetroButton.create(Component.translatable("gui.done"), button -> onClose(), x, y + 188, width, 20));
    }

    private static Component modeLabel() {
        String mode = WishingWillowClientConfig.FULFILLMENT_MODE.get().name().toLowerCase(Locale.ROOT);
        return Component.translatable("screen.wishing_willow.music.mode",
                Component.translatable("screen.wishing_willow.music.mode." + mode));
    }
    private static Component masterLabel() { return toggleLabel("cinematic", WishingWillowClientConfig.CINEMATIC_MUSIC.get()); }
    private static Component volumeLabel() {
        return Component.translatable("screen.wishing_willow.music.volume", WishingWillowClientConfig.MUSIC_VOLUME.get());
    }
    private static Component intensityLabel() {
        String intensity = WishingWillowClientConfig.CINEMATIC_FILTER_INTENSITY.get()
                .name().toLowerCase(Locale.ROOT);
        return Component.translatable("screen.wishing_willow.music.filter_intensity",
                Component.translatable("screen.wishing_willow.music.filter_intensity." + intensity));
    }
    private static Component toggleLabel(String name, boolean value) {
        return Component.translatable("screen.wishing_willow.music." + name,
                Component.translatable("screen.wishing_willow.music." + (value ? "on" : "off")));
    }
    private static void save() { WishingWillowClientConfig.SPEC.save(); }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        RetroUiTheme.drawBackdrop(graphics); RetroUiTheme.drawHeader(graphics, font, title, width / 2, 10);
        super.render(graphics, mouseX, mouseY, partialTick);
    }
    @Override public void onClose() { if (minecraft != null) minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
