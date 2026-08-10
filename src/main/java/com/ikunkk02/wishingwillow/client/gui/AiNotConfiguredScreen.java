package com.ikunkk02.wishingwillow.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;

public final class AiNotConfiguredScreen extends Screen {
    @Nullable
    private final Screen parent;

    public AiNotConfiguredScreen(@Nullable Screen parent) {
        super(Component.translatable("screen.wishing_willow.ai.not_configured.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int centerX = width / 2;
        int y = height / 2 + 22;
        addRenderableWidget(Button.builder(
                        Component.translatable("screen.wishing_willow.ai.open_settings"),
                        button -> minecraft.setScreen(new AiSettingsScreen(this))
                )
                .bounds(centerX - 106, y, 100, 20)
                .build());
        addRenderableWidget(Button.builder(
                        Component.translatable("screen.wishing_willow.cancel"),
                        button -> onClose()
                )
                .bounds(centerX + 6, y, 100, 20)
                .build());
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.drawCenteredString(font, title, width / 2, height / 2 - 42, 0xFFFFFFFF);
        graphics.drawCenteredString(
                font,
                Component.translatable("screen.wishing_willow.ai.not_configured.message"),
                width / 2,
                height / 2 - 16,
                0xFFD0CBC3
        );
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
