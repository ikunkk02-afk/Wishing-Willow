package com.ikunkk02.wishingwillow.client.gui;

import com.ikunkk02.wishingwillow.research.ResearchConfig;
import com.ikunkk02.wishingwillow.research.ResearchConfigManager;
import com.ikunkk02.wishingwillow.research.ModResearchManager;
import com.ikunkk02.wishingwillow.research.ResearchState;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class ResearchSettingsScreen extends Screen {
    private final Screen parent;
    private PasswordEditBox apiKey;
    private Component status = Component.translatable("screen.wishing_willow.research.key.optional");

    public ResearchSettingsScreen(Screen parent) {
        super(Component.translatable("screen.wishing_willow.research.settings.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int fieldWidth = Math.min(360, width - 48);
        int x = (width - fieldWidth) / 2;
        int y = Math.max(38, Math.min(height - 82, height / 2 - 44));
        apiKey = new PasswordEditBox(font, x, y, fieldWidth - 66, 20,
                Component.translatable("screen.wishing_willow.research.curseforge_key"));
        apiKey.setMaxLength(ResearchConfig.MAX_KEY_LENGTH);
        apiKey.setValue(ResearchConfigManager.getInstance().get().curseForgeApiKey());
        addRenderableWidget(apiKey);
        addRenderableWidget(RetroButton.create(Component.translatable("screen.wishing_willow.ai.show"), button -> {
                    apiKey.setPasswordVisible(!apiKey.isPasswordVisible());
                    button.setMessage(Component.translatable(apiKey.isPasswordVisible()
                            ? "screen.wishing_willow.ai.hide" : "screen.wishing_willow.ai.show"));
                }, x + fieldWidth - 62, y, 62, 20));
        addRenderableWidget(RetroButton.create(Component.translatable("screen.wishing_willow.ai.save"),
                button -> save(), width / 2 - 106, y + 54, 100, 20));
        addRenderableWidget(RetroButton.create(Component.translatable("screen.wishing_willow.cancel"),
                button -> onClose(), width / 2 + 6, y + 54, 100, 20));
    }

    private void save() {
        try {
            if (ResearchConfigManager.getInstance().save(new ResearchConfig(apiKey.getValue()))) {
                onClose();
            } else {
                status = Component.translatable("screen.wishing_willow.research.save_failed");
            }
        } catch (IllegalArgumentException exception) {
            status = Component.translatable("screen.wishing_willow.research.save_failed");
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        RetroUiTheme.drawBackdrop(graphics);
        int panelWidth = Math.min(430, Math.max(190, width - 12));
        RetroUiTheme.drawPaperPanel(graphics, (width - panelWidth) / 2, 3, panelWidth, height - 7);
        graphics.drawCenteredString(font, title, width / 2, 13, RetroUiTheme.OXBLOOD_DARK);
        graphics.drawString(font, Component.translatable("screen.wishing_willow.research.curseforge_key"),
                (width - Math.min(360, width - 48)) / 2,
                Math.max(38, Math.min(height - 82, height / 2 - 44)) - 12, RetroUiTheme.INK);
        graphics.drawCenteredString(font, status, width / 2,
                Math.max(38, Math.min(height - 82, height / 2 - 44)) + 28, RetroUiTheme.MUTED_INK);
        if (height >= 170) {
            var snapshot = ModResearchManager.getInstance().knowledgeBase().snapshot();
            int total = (int) snapshot.entries().stream()
                    .filter(entry -> entry.state() != ResearchState.IGNORED).count();
            int ready = (int) snapshot.count(ResearchState.READY);
            int barWidth = Math.min(300, panelWidth - 36);
            int barX = (width - barWidth) / 2;
            int barY = 39;
            graphics.drawCenteredString(font, Component.translatable("screen.wishing_willow.research.progress",
                    ready, total), width / 2, 26, RetroUiTheme.INK);
            graphics.fill(barX, barY, barX + barWidth, barY + 6, 0x55482F26);
            if (total > 0) graphics.fill(barX, barY, barX + barWidth * ready / total, barY + 6,
                    RetroUiTheme.STATUS_OK);
            graphics.drawCenteredString(font, ResearchUiText.baseState(snapshot.state()), width / 2,
                    barY + 10, snapshot.paused() ? RetroUiTheme.STATUS_WARN : RetroUiTheme.MUTED_INK);
        }
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
