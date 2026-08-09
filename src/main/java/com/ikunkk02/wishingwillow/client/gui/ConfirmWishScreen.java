package com.ikunkk02.wishingwillow.client.gui;

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
        addRenderableWidget(Button.builder(
                        Component.translatable("screen.wishing_willow.confirm.accept"),
                        button -> submit()
                )
                .bounds(centerX - 106, buttonY, 100, 20)
                .build());
        addRenderableWidget(Button.builder(
                        Component.translatable("screen.wishing_willow.confirm.back"),
                        button -> goBack()
                )
                .bounds(centerX + 6, buttonY, 100, 20)
                .build());
    }

    private void submit() {
        if (submitted) {
            return;
        }
        submitted = true;
        ModNetworking.sendToServer(new SubmitWishPacket(UUID.randomUUID(), hand, wish));
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
        renderBackground(graphics);
        int panelWidth = Math.min(330, width - 32);
        int panelHeight = 118;
        int left = (width - panelWidth) / 2;
        int top = (height - panelHeight) / 2;
        graphics.fill(left, top, left + panelWidth, top + panelHeight, 0xE31A1816);
        graphics.renderOutline(left, top, panelWidth, panelHeight, 0xFF62594F);
        graphics.drawCenteredString(font, title, width / 2, top + 18, 0xFFD6D2CB);
        graphics.drawCenteredString(
                font,
                Component.translatable("screen.wishing_willow.confirm.warning"),
                width / 2,
                top + 46,
                0xFFB9B4AC
        );
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
