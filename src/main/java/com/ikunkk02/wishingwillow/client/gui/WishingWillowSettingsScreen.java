package com.ikunkk02.wishingwillow.client.gui;

import net.minecraft.client.gui.GuiGraphics;
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
        boolean compact = height < 180;
        int panelWidth = Math.min(430, Math.max(190, width - 12));
        int columns = compact ? 2 : 1;
        int gap = 6;
        int buttonWidth = columns == 1 ? Math.min(240, panelWidth - 28) : (panelWidth - 34) / 2;
        int startX = (width - (buttonWidth * columns + gap * (columns - 1))) / 2;
        int y = compact ? 31 : Math.max(39, height / 2 - 68);
        Component[] labels = {
                Component.translatable("screen.wishing_willow.settings.ai"),
                Component.translatable("screen.wishing_willow.settings.knowledge"),
                Component.translatable("screen.wishing_willow.settings.research"),
                Component.translatable("screen.wishing_willow.settings.execution"),
                Component.translatable("screen.wishing_willow.settings.music")
        };
        net.minecraft.client.gui.components.Button.OnPress[] actions = new net.minecraft.client.gui.components.Button.OnPress[]{
                button -> minecraft.setScreen(new AiSettingsScreen(this)),
                button -> minecraft.setScreen(new ModKnowledgeBaseScreen(this)),
                button -> minecraft.setScreen(new ResearchSettingsScreen(this)),
                button -> minecraft.setScreen(new ExecutionSettingsScreen(this)),
                button -> minecraft.setScreen(new MusicSettingsScreen(this))
        };
        for (int index = 0; index < labels.length; index++) {
            addRenderableWidget(RetroButton.create(labels[index], actions[index],
                    startX + (index % columns) * (buttonWidth + gap),
                    y + (index / columns) * 26, buttonWidth, 20));
        }
        int doneY = y + ((labels.length + columns - 1) / columns) * 26 + 5;
        addRenderableWidget(RetroButton.create(Component.translatable("gui.done"), button -> onClose(),
                width / 2 - buttonWidth / 2, doneY, buttonWidth, 20));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        RetroUiTheme.drawBackdrop(graphics);
        int panelWidth = Math.min(430, Math.max(190, width - 12));
        RetroUiTheme.drawPaperPanel(graphics, (width - panelWidth) / 2, 3, panelWidth, height - 7);
        RetroUiTheme.drawHeader(graphics, font, title, width / 2, 10);
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
