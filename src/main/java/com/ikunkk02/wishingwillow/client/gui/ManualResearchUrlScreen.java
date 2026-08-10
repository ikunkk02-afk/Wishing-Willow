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
        int width = Math.min(500, this.width - 40), x = (this.width - width) / 2, y = this.height / 2 - 20;
        url = new EditBox(font, x, y, width, 20, Component.translatable("screen.wishing_willow.knowledge.manual_url"));
        url.setMaxLength(2048); addRenderableWidget(url); setInitialFocus(url);
        addRenderableWidget(Button.builder(Component.translatable("screen.wishing_willow.knowledge.manual_url.submit"),
                button -> submit()).bounds(this.width / 2 - 106, y + 34, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.wishing_willow.cancel"), button -> onClose())
                .bounds(this.width / 2 + 6, y + 34, 100, 20).build());
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
        renderBackground(graphics); graphics.drawCenteredString(font, title, width / 2, height / 2 - 58, 0xFFFFFFFF);
        graphics.drawCenteredString(font, status, width / 2, height / 2 + 42, 0xFFAAA49B);
        super.render(graphics, mouseX, mouseY, partialTick);
    }
    @Override public void onClose() { if (minecraft != null) minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
