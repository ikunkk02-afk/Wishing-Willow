package com.ikunkk02.wishingwillow.client.gui;

import com.ikunkk02.wishingwillow.wish.WishTextValidator;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;

public final class WishScreen extends Screen {
    private static final int PANEL_WIDTH = 360;
    private static final int PANEL_HEIGHT = 238;

    private final InteractionHand hand;
    private final String initialValue;
    private MultiLineEditBox wishInput;
    private Button wishButton;
    private int panelLeft;
    private int panelTop;
    private int panelWidth;
    private int panelHeight;

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
        panelWidth = Math.min(PANEL_WIDTH, width - 32);
        panelHeight = Math.min(PANEL_HEIGHT, height - 24);
        panelLeft = (width - panelWidth) / 2;
        panelTop = (height - panelHeight) / 2;

        int inputHeight = Math.max(82, panelHeight - 108);
        wishInput = new MultiLineEditBox(
                font,
                panelLeft + 18,
                panelTop + 42,
                panelWidth - 36,
                inputHeight,
                Component.translatable("screen.wishing_willow.wish.placeholder"),
                Component.translatable("screen.wishing_willow.wish.input")
        );
        wishInput.setCharacterLimit(WishTextValidator.MAX_LENGTH);
        wishInput.setValue(initialValue);
        wishInput.setValueListener(value -> wishButton.active = !value.strip().isEmpty());
        addRenderableWidget(wishInput);

        int buttonY = panelTop + panelHeight - 32;
        wishButton = addRenderableWidget(Button.builder(
                        Component.translatable("screen.wishing_willow.wish.submit"),
                        button -> openConfirmation()
                )
                .bounds(panelLeft + panelWidth / 2 - 106, buttonY, 100, 20)
                .build());
        wishButton.active = !initialValue.strip().isEmpty();

        addRenderableWidget(Button.builder(
                        Component.translatable("screen.wishing_willow.cancel"),
                        button -> onClose()
                )
                .bounds(panelLeft + panelWidth / 2 + 6, buttonY, 100, 20)
                .build());
        setInitialFocus(wishInput);
    }

    private void openConfirmation() {
        String wish = wishInput.getValue();
        if (!wish.strip().isEmpty() && minecraft != null) {
            minecraft.setScreen(new ConfirmWishScreen(hand, wish));
        }
    }

    @Override
    public void tick() {
        wishInput.tick();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.fill(panelLeft, panelTop, panelLeft + panelWidth, panelTop + panelHeight, 0xDC171513);
        graphics.fill(panelLeft, panelTop, panelLeft + panelWidth, panelTop + 1, 0xFF62594F);
        graphics.fill(panelLeft, panelTop + panelHeight - 1, panelLeft + panelWidth, panelTop + panelHeight, 0xFF3B3530);
        graphics.fill(panelLeft, panelTop, panelLeft + 1, panelTop + panelHeight, 0xFF62594F);
        graphics.fill(panelLeft + panelWidth - 1, panelTop, panelLeft + panelWidth, panelTop + panelHeight, 0xFF3B3530);
        graphics.drawCenteredString(font, title, width / 2, panelTop + 16, 0xFFD6D2CB);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
