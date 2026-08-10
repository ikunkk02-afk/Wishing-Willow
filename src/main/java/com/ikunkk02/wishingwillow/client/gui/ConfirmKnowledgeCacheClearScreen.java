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
        addRenderableWidget(Button.builder(Component.translatable("screen.wishing_willow.knowledge.clear_confirm.yes"),
                        button -> {
                            ModResearchManager.getInstance().clearCache();
                            onClose();
                        }).bounds(width / 2 - 106, y, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.wishing_willow.cancel"), button -> onClose())
                .bounds(width / 2 + 6, y, 100, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.drawCenteredString(font, title, width / 2, height / 2 - 24, 0xFFFFFFFF);
        graphics.drawCenteredString(font, Component.translatable("screen.wishing_willow.knowledge.clear_confirm.message"),
                width / 2, height / 2 - 8, 0xFFD6D2CB);
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
