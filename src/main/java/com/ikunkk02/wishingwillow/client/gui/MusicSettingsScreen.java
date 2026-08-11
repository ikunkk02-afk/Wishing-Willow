package com.ikunkk02.wishingwillow.client.gui;

import com.ikunkk02.wishingwillow.ai.WishFulfillmentMode;
import com.ikunkk02.wishingwillow.client.music.WishingWillowMusicController;
import com.ikunkk02.wishingwillow.config.WishingWillowClientConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class MusicSettingsScreen extends Screen {
    private final Screen parent;
    public MusicSettingsScreen(Screen parent) { super(Component.literal("Wishing Willow Experience")); this.parent = parent; }

    @Override protected void init() {
        int width = Math.min(280, this.width - 24), x = (this.width - width) / 2, y = Math.max(28, this.height / 2 - 76);
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
        addRenderableWidget(RetroButton.create(toggleLabel("Trade reveal music", WishingWillowClientConfig.TRADE_REVEAL_MUSIC.get()), button -> {
            WishingWillowClientConfig.TRADE_REVEAL_MUSIC.set(!WishingWillowClientConfig.TRADE_REVEAL_MUSIC.get());
            save(); button.setMessage(toggleLabel("Trade reveal music", WishingWillowClientConfig.TRADE_REVEAL_MUSIC.get()));
        }, x, y + 78, width, 20));
        addRenderableWidget(RetroButton.create(toggleLabel("Wish sequence music", WishingWillowClientConfig.WISH_SEQUENCE_MUSIC.get()), button -> {
            WishingWillowClientConfig.WISH_SEQUENCE_MUSIC.set(!WishingWillowClientConfig.WISH_SEQUENCE_MUSIC.get());
            if (!WishingWillowClientConfig.WISH_SEQUENCE_MUSIC.get()) WishingWillowMusicController.clear();
            save(); button.setMessage(toggleLabel("Wish sequence music", WishingWillowClientConfig.WISH_SEQUENCE_MUSIC.get()));
        }, x, y + 104, width, 20));
        addRenderableWidget(RetroButton.create(Component.translatable("gui.done"), button -> onClose(), x, y + 136, width, 20));
    }

    private static Component modeLabel() { return Component.literal("Wish Fulfillment Mode: " + WishingWillowClientConfig.FULFILLMENT_MODE.get()); }
    private static Component masterLabel() { return toggleLabel("Cinematic soundtrack", WishingWillowClientConfig.CINEMATIC_MUSIC.get()); }
    private static Component volumeLabel() { return Component.literal("Music volume: " + WishingWillowClientConfig.MUSIC_VOLUME.get() + "%"); }
    private static Component toggleLabel(String name, boolean value) { return Component.literal(name + ": " + (value ? "ON" : "OFF")); }
    private static void save() { WishingWillowClientConfig.SPEC.save(); }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        RetroUiTheme.drawBackdrop(graphics); RetroUiTheme.drawHeader(graphics, font, title, width / 2, 10);
        super.render(graphics, mouseX, mouseY, partialTick);
    }
    @Override public void onClose() { if (minecraft != null) minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
