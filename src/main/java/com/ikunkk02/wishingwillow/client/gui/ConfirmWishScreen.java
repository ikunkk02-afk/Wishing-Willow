package com.ikunkk02.wishingwillow.client.gui;

import com.ikunkk02.wishingwillow.ai.AiConfig;
import com.ikunkk02.wishingwillow.ai.AiConfigManager;
import com.ikunkk02.wishingwillow.network.ModNetworking;
import com.ikunkk02.wishingwillow.network.packet.SubmitWishPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;

import java.util.UUID;

public final class ConfirmWishScreen extends Screen {
    private final InteractionHand hand;
    private final String wish;
    private boolean submitted;

    public ConfirmWishScreen(InteractionHand hand, String wish) {
        super(Component.translatable("screen.wishing_willow.confirm.title"));
        this.hand = hand;
        this.wish = wish;
    }

    @Override
    protected void init() {
        int panelWidth = Math.min(330, width - 32);
        int centerX = width / 2;
        int buttonY = height / 2 + 32;
        int buttonWidth = Math.min(100, (panelWidth - 18) / 2);
        addRenderableWidget(RetroButton.create(Component.translatable("screen.wishing_willow.confirm.accept"),
                button -> submit(), centerX - buttonWidth - 3, buttonY, buttonWidth, 20));
        addRenderableWidget(RetroButton.create(Component.translatable("screen.wishing_willow.confirm.back"),
                button -> goBack(), centerX + 3, buttonY, buttonWidth, 20));
    }

    private void submit() {
        if (submitted) {
            return;
        }
        submitted = true;
        AiConfig config = AiConfigManager.getInstance().get();
        if (!config.isConfigured()) {
            submitted = false;
            if (minecraft != null) {
                minecraft.setScreen(new AiNotConfiguredScreen(this));
            }
            return;
        }
        ModNetworking.sendToServer(new SubmitWishPacket(
                UUID.randomUUID(), hand, wish,
                config.executionMode(), config.providerType(), config.model()
        ));
        if (minecraft != null) {
            minecraft.setScreen(null);
        }
    }

    private void goBack() {
        if (minecraft != null) {
            minecraft.setScreen(new WishScreen(hand, wish));
        }
    }

    @Override
    public void onClose() {
        goBack();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        RetroUiTheme.drawBackdrop(graphics);
        int panelWidth = Math.min(330, width - 32);
        int panelHeight = 118;
        int left = (width - panelWidth) / 2;
        int top = (height - panelHeight) / 2;
        RetroUiTheme.drawPaperPanel(graphics, left, top, panelWidth, panelHeight);
        graphics.drawCenteredString(font, title, width / 2, top + 18, RetroUiTheme.OXBLOOD_DARK);
        graphics.drawCenteredString(
                font,
                Component.translatable("screen.wishing_willow.confirm.warning"),
                width / 2,
                top + 46,
                RetroUiTheme.INK
        );
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
