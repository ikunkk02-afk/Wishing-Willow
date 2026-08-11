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
        int y = Math.min(height - 28, height / 2 + 22);
        int buttonWidth = Math.min(100, (Math.max(190, width - 20) - 18) / 2);
        addRenderableWidget(RetroButton.create(Component.translatable("screen.wishing_willow.ai.open_settings"),
                button -> minecraft.setScreen(new AiSettingsScreen(this)),
                centerX - buttonWidth - 3, y, buttonWidth, 20));
        addRenderableWidget(RetroButton.create(Component.translatable("screen.wishing_willow.cancel"),
                button -> onClose(), centerX + 3, y, buttonWidth, 20));
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        RetroUiTheme.drawBackdrop(graphics);
        int panelWidth = Math.min(390, Math.max(190, width - 12));
        int panelHeight = Math.min(130, height - 8);
        int panelY = (height - panelHeight) / 2;
        RetroUiTheme.drawPaperPanel(graphics, (width - panelWidth) / 2, panelY, panelWidth, panelHeight);
        graphics.drawCenteredString(font, title, width / 2, panelY + 13, RetroUiTheme.OXBLOOD_DARK);
        int lineY = panelY + 34;
        for (net.minecraft.util.FormattedCharSequence line : font.split(
                Component.translatable("screen.wishing_willow.ai.not_configured.message"), panelWidth - 28)) {
            graphics.drawCenteredString(font, line, width / 2, lineY, RetroUiTheme.INK);
            lineY += 11;
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
