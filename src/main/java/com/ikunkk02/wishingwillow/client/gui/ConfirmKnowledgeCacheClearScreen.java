package com.ikunkk02.wishingwillow.client.gui;

import com.ikunkk02.wishingwillow.research.ModResearchManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class ConfirmKnowledgeCacheClearScreen extends Screen {
    private final Screen parent;

    public ConfirmKnowledgeCacheClearScreen(Screen parent) {
        super(Component.translatable("screen.wishing_willow.knowledge.clear_confirm.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int y = height / 2 + 12;
        int buttonWidth = Math.min(100, (Math.max(190, width - 20) - 18) / 2);
        addRenderableWidget(RetroButton.create(Component.translatable("screen.wishing_willow.knowledge.clear_confirm.yes"),
                        button -> {
                            ModResearchManager.getInstance().clearCache();
                            onClose();
                        }, width / 2 - buttonWidth - 3, y, buttonWidth, 20));
        addRenderableWidget(RetroButton.create(Component.translatable("screen.wishing_willow.cancel"),
                button -> onClose(), width / 2 + 3, y, buttonWidth, 20));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        RetroUiTheme.drawBackdrop(graphics);
        int panelWidth = Math.min(360, Math.max(190, width - 12));
        int panelHeight = Math.min(112, height - 8);
        int panelY = (height - panelHeight) / 2;
        RetroUiTheme.drawPaperPanel(graphics, (width - panelWidth) / 2, panelY, panelWidth, panelHeight);
        graphics.drawCenteredString(font, title, width / 2, height / 2 - 24, RetroUiTheme.OXBLOOD_DARK);
        graphics.drawCenteredString(font, Component.translatable("screen.wishing_willow.knowledge.clear_confirm.message"),
                width / 2, height / 2 - 8, RetroUiTheme.INK);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
