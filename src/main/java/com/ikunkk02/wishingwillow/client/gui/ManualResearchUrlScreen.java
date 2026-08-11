package com.ikunkk02.wishingwillow.client.gui;

import com.ikunkk02.wishingwillow.research.ModResearchManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class ManualResearchUrlScreen extends Screen {
    private final Screen parent;
    private final String modId;
    private EditBox url;
    private Component status = Component.translatable("screen.wishing_willow.knowledge.manual_url.help");

    public ManualResearchUrlScreen(Screen parent, String modId) {
        super(Component.translatable("screen.wishing_willow.knowledge.manual_url.title"));
        this.parent = parent; this.modId = modId;
    }

    @Override protected void init() {
        int width = Math.min(500, Math.max(170, this.width - 28)), x = (this.width - width) / 2;
        int y = Math.max(34, Math.min(this.height - 63, this.height / 2 - 20));
        url = new EditBox(font, x, y, width, 20, Component.translatable("screen.wishing_willow.knowledge.manual_url"));
        url.setMaxLength(2048); addRenderableWidget(url); setInitialFocus(url);
        int buttonWidth = Math.min(100, (width - 10) / 2);
        addRenderableWidget(RetroButton.create(Component.translatable("screen.wishing_willow.knowledge.manual_url.submit"),
                button -> submit(), this.width / 2 - buttonWidth - 3, y + 34, buttonWidth, 20));
        addRenderableWidget(RetroButton.create(Component.translatable("screen.wishing_willow.cancel"),
                button -> onClose(), this.width / 2 + 3, y + 34, buttonWidth, 20));
    }
    private void submit() {
        String value = url.getValue().strip();
        if (!value.toLowerCase(java.util.Locale.ROOT).startsWith("https://")) {
            status = Component.translatable("screen.wishing_willow.knowledge.manual_url.invalid"); return;
        }
        if (ModResearchManager.getInstance().researchWeb(modId, value)) {
            if (minecraft != null) minecraft.setScreen(parent);
        } else status = Component.translatable("screen.wishing_willow.knowledge.manual_url.invalid");
    }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        RetroUiTheme.drawBackdrop(graphics);
        int panelWidth = Math.min(540, Math.max(190, width - 12));
        RetroUiTheme.drawPaperPanel(graphics, (width - panelWidth) / 2, 3, panelWidth, height - 7);
        graphics.drawCenteredString(font, title, width / 2, 12, RetroUiTheme.OXBLOOD_DARK);
        graphics.drawCenteredString(font, status, width / 2, height - 13, RetroUiTheme.MUTED_INK);
        super.render(graphics, mouseX, mouseY, partialTick);
    }
    @Override public void onClose() { if (minecraft != null) minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
