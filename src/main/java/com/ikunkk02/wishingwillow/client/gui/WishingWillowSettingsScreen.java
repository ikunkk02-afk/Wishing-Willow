package com.ikunkk02.wishingwillow.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;

public final class WishingWillowSettingsScreen extends Screen {
    @Nullable
    private final Screen parent;

    public WishingWillowSettingsScreen(@Nullable Screen parent) {
        super(Component.translatable("screen.wishing_willow.settings.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int x = width / 2 - 100;
        int y = Math.max(48, height / 2 - 50);
        addRenderableWidget(Button.builder(Component.translatable("screen.wishing_willow.settings.ai"),
                        button -> minecraft.setScreen(new AiSettingsScreen(this)))
                .bounds(x, y, 200, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.wishing_willow.settings.knowledge"),
                        button -> minecraft.setScreen(new ModKnowledgeBaseScreen(this)))
                .bounds(x, y + 26, 200, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.wishing_willow.settings.research"),
                        button -> minecraft.setScreen(new ResearchSettingsScreen(this)))
                .bounds(x, y + 52, 200, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(x, y + 88, 200, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.drawCenteredString(font, title, width / 2, 24, 0xFFFFFFFF);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
