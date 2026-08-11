package com.ikunkk02.wishingwillow.client.gui;

import com.ikunkk02.wishingwillow.ai.AiConfig;
import com.ikunkk02.wishingwillow.ai.AiConfigManager;
import com.ikunkk02.wishingwillow.network.ModNetworking;
import com.ikunkk02.wishingwillow.network.packet.SubmitWishPacket;
import com.ikunkk02.wishingwillow.wish.WishTextValidator;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;

import java.util.UUID;
import com.ikunkk02.wishingwillow.client.music.WishingWillowMusicController;

public final class WishScreen extends Screen {
    private final InteractionHand hand;
    private final String initialValue;
    private MultiLineEditBox wishInput;
    private RetroButton wishButton;
    private RetroButton cancelButton;
    private RetroButton confirmButton;
    private RetroButton backButton;
    private int panelLeft;
    private int panelTop;
    private int panelWidth;
    private int panelHeight;
    private int inputBottom;
    private boolean confirming;
    private boolean submitted;

    public WishScreen(InteractionHand hand) {
        this(hand, "");
    }

    public WishScreen(InteractionHand hand, String initialValue) {
        super(Component.translatable("screen.wishing_willow.wish.title"));
        this.hand = hand;
        this.initialValue = initialValue;
    }

    @Override
    protected void init() {
        WishingWillowMusicController.startWishSequence();
        panelWidth = Math.min(420, Math.max(190, width - 12));
        panelHeight = Math.min(270, Math.max(106, height - 12));
        panelLeft = (width - panelWidth) / 2;
        panelTop = (height - panelHeight) / 2;
        int buttonY = panelTop + panelHeight - 27;
        int inputTop = panelTop + (panelHeight >= 160 ? 43 : 28);
        inputBottom = buttonY - (panelHeight >= 180 ? 24 : 7);
        int inputHeight = Math.max(24, inputBottom - inputTop);

        wishInput = new MultiLineEditBox(font, panelLeft + 14, inputTop, panelWidth - 28, inputHeight,
                Component.translatable("screen.wishing_willow.wish.placeholder"),
                Component.translatable("screen.wishing_willow.wish.input"));
        wishInput.setCharacterLimit(WishTextValidator.MAX_LENGTH);
        wishInput.setValue(initialValue);
        wishInput.setValueListener(value -> wishButton.active = !value.strip().isEmpty());
        addRenderableWidget(wishInput);

        int gap = 8;
        int buttonWidth = Math.min(112, (panelWidth - 36 - gap) / 2);
        int firstX = panelLeft + panelWidth / 2 - buttonWidth - gap / 2;
        wishButton = addRenderableWidget(RetroButton.create(
                Component.translatable("screen.wishing_willow.wish.submit"), button -> showConfirmation(),
                firstX, buttonY, buttonWidth, 20));
        wishButton.active = !initialValue.strip().isEmpty();
        cancelButton = addRenderableWidget(RetroButton.create(
                Component.translatable("screen.wishing_willow.cancel"), button -> onClose(),
                firstX + buttonWidth + gap, buttonY, buttonWidth, 20));

        int modalButtonY = panelTop + panelHeight / 2 + 22;
        confirmButton = addRenderableWidget(RetroButton.create(
                Component.translatable("screen.wishing_willow.confirm.accept"), button -> submit(),
                firstX, modalButtonY, buttonWidth, 20));
        backButton = addRenderableWidget(RetroButton.create(
                Component.translatable("screen.wishing_willow.confirm.back"), button -> hideConfirmation(),
                firstX + buttonWidth + gap, modalButtonY, buttonWidth, 20));
        setConfirmationWidgets(false);
        setInitialFocus(wishInput);
    }

    private void showConfirmation() {
        if (!wishInput.getValue().strip().isEmpty()) {
            confirming = true;
            setConfirmationWidgets(true);
        }
    }

    private void hideConfirmation() {
        confirming = false;
        setConfirmationWidgets(false);
        setFocused(wishInput);
    }

    private void setConfirmationWidgets(boolean modal) {
        wishInput.active = !modal;
        wishInput.visible = !modal;
        wishButton.visible = !modal;
        wishButton.active = !modal && !wishInput.getValue().strip().isEmpty();
        cancelButton.visible = !modal;
        confirmButton.visible = modal;
        confirmButton.active = modal;
        backButton.visible = modal;
        backButton.active = modal;
    }

    private void submit() {
        if (submitted) {
            return;
        }
        AiConfig config = AiConfigManager.getInstance().get();
        if (!config.isConfigured()) {
            if (minecraft != null) {
                minecraft.setScreen(new AiNotConfiguredScreen(this));
            }
            return;
        }
        submitted = true;
        ModNetworking.sendToServer(new SubmitWishPacket(UUID.randomUUID(), hand, wishInput.getValue(),
                config.executionMode(), config.providerType(), config.model()));
        if (minecraft != null) {
            minecraft.setScreen(null);
        }
    }

    @Override
    public void tick() {
        wishInput.tick();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (confirming && keyCode == 256) {
            hideConfirmation();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (confirming) {
            if (confirmButton.mouseClicked(mouseX, mouseY, button)) return true;
            if (backButton.mouseClicked(mouseX, mouseY, button)) return true;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        RetroUiTheme.drawBackdrop(graphics);
        RetroUiTheme.drawPaperPanel(graphics, panelLeft, panelTop, panelWidth, panelHeight);
        RetroUiTheme.drawHeader(graphics, font, Component.literal("ONE WISH WILLOW"),
                width / 2, panelTop + 9);
        if (!confirming && panelHeight >= 160) {
            graphics.drawCenteredString(font, Component.translatable("screen.wishing_willow.wish.title"),
                    width / 2, panelTop + 29, RetroUiTheme.OXBLOOD_DARK);
        }
        if (confirming) {
            graphics.fill(panelLeft + 4, panelTop + 4, panelLeft + panelWidth - 4,
                    panelTop + panelHeight - 4, 0x941B110E);
            int modalWidth = Math.min(310, panelWidth - 24);
            int modalHeight = Math.min(104, panelHeight - 18);
            int modalX = (width - modalWidth) / 2;
            int modalY = panelTop + (panelHeight - modalHeight) / 2;
            RetroUiTheme.drawPaperPanel(graphics, modalX, modalY, modalWidth, modalHeight);
            graphics.drawCenteredString(font, Component.translatable("screen.wishing_willow.confirm.title"),
                    width / 2, modalY + 14, RetroUiTheme.OXBLOOD_DARK);
            graphics.drawCenteredString(font, Component.translatable("screen.wishing_willow.confirm.warning"),
                    width / 2, modalY + 36, RetroUiTheme.INK);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        if (!confirming) {
            String count = wishInput.getValue().length() + " / " + WishTextValidator.MAX_LENGTH;
            graphics.drawString(font, count, panelLeft + panelWidth - 15 - font.width(count),
                    inputBottom - 10, RetroUiTheme.MUTED_INK, false);
            if (panelHeight >= 180) {
                graphics.drawCenteredString(font,
                        Component.translatable("screen.wishing_willow.confirm.warning"), width / 2,
                        panelTop + panelHeight - 41, RetroUiTheme.MUTED_INK);
            }
        }
    }

    @Override
    public void onClose() {
        if (!submitted) WishingWillowMusicController.cancelWishSequence();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
