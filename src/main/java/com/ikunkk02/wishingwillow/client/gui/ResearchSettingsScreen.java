package com.ikunkk02.wishingwillow.client.gui;

import com.ikunkk02.wishingwillow.research.ResearchConfig;
import com.ikunkk02.wishingwillow.research.ResearchConfigManager;
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
        int y = Math.max(56, height / 2 - 44);
        apiKey = new PasswordEditBox(font, x, y, fieldWidth - 66, 20,
                Component.translatable("screen.wishing_willow.research.curseforge_key"));
        apiKey.setMaxLength(ResearchConfig.MAX_KEY_LENGTH);
        apiKey.setValue(ResearchConfigManager.getInstance().get().curseForgeApiKey());
        addRenderableWidget(apiKey);
        addRenderableWidget(Button.builder(Component.translatable("screen.wishing_willow.ai.show"), button -> {
                    apiKey.setPasswordVisible(!apiKey.isPasswordVisible());
                    button.setMessage(Component.translatable(apiKey.isPasswordVisible()
                            ? "screen.wishing_willow.ai.hide" : "screen.wishing_willow.ai.show"));
                }).bounds(x + fieldWidth - 62, y, 62, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.wishing_willow.ai.save"), button -> save())
                .bounds(width / 2 - 106, y + 54, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.wishing_willow.cancel"), button -> onClose())
                .bounds(width / 2 + 6, y + 54, 100, 20).build());
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
        renderBackground(graphics);
        graphics.drawCenteredString(font, title, width / 2, 24, 0xFFFFFFFF);
        graphics.drawString(font, Component.translatable("screen.wishing_willow.research.curseforge_key"),
                (width - Math.min(360, width - 48)) / 2, Math.max(56, height / 2 - 44) - 12, 0xFFD6D2CB);
        graphics.drawCenteredString(font, status, width / 2, Math.max(56, height / 2 - 44) + 28, 0xFFAAA49B);
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
