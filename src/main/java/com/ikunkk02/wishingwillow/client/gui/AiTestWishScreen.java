package com.ikunkk02.wishingwillow.client.gui;

import com.ikunkk02.wishingwillow.ai.AiConfig;
import com.ikunkk02.wishingwillow.ai.AiErrorCategory;
import com.ikunkk02.wishingwillow.ai.AiService;
import com.ikunkk02.wishingwillow.ai.WishInterpretationResult;
import com.ikunkk02.wishingwillow.ai.WishInterpreter;
import com.ikunkk02.wishingwillow.wish.WishTextValidator;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class AiTestWishScreen extends Screen {
    private final Screen parent;
    private final AiConfig config;
    private MultiLineEditBox input;
    private Button testButton;
    private Component status = Component.translatable("screen.wishing_willow.ai.test.ready");
    private boolean running;
    private int panelWidth;
    private int panelLeft;
    private int panelTop;
    private int panelHeight;
    private int statusY;

    public AiTestWishScreen(Screen parent, AiConfig config) {
        super(Component.translatable("screen.wishing_willow.ai.test.title"));
        this.parent = parent;
        this.config = config;
    }

    @Override
    protected void init() {
        panelWidth = Math.min(400, Math.max(190, width - 12));
        panelLeft = (width - panelWidth) / 2;
        panelTop = 3;
        panelHeight = height - 7;
        int buttonY = height - 27;
        int inputTop = panelTop + 29;
        statusY = buttonY - 14;
        int inputHeight = Math.max(24, statusY - inputTop - 3);
        input = new MultiLineEditBox(
                font, panelLeft + 12, inputTop, panelWidth - 24, inputHeight,
                Component.translatable("screen.wishing_willow.ai.test.placeholder"),
                Component.translatable("screen.wishing_willow.ai.test.input")
        );
        input.setCharacterLimit(WishTextValidator.MAX_LENGTH);
        input.setValue(Component.translatable("screen.wishing_willow.ai.test.example").getString());
        input.setValueListener(value -> testButton.active = !running && !value.strip().isEmpty());
        addRenderableWidget(input);
        int buttonWidth = Math.min(100, (panelWidth - 30) / 2);
        testButton = addRenderableWidget(RetroButton.create(
                Component.translatable("screen.wishing_willow.ai.test.run"), button -> runTest(),
                width / 2 - buttonWidth - 3, buttonY, buttonWidth, 20));
        addRenderableWidget(RetroButton.create(Component.translatable("screen.wishing_willow.cancel"),
                button -> onClose(), width / 2 + 3, buttonY, buttonWidth, 20));
    }

    private void runTest() {
        if (running || input.getValue().strip().isEmpty()) {
            return;
        }
        running = true;
        testButton.active = false;
        status = Component.translatable("screen.wishing_willow.ai.status.testing_interpretation");
        String wish = input.getValue();
        new WishInterpreter(AiService.getInstance()).interpret(config, wish).whenComplete((result, throwable) -> {
            if (minecraft == null) {
                return;
            }
            minecraft.execute(() -> {
                if (minecraft.screen != this) {
                    return;
                }
                running = false;
                testButton.active = !input.getValue().strip().isEmpty();
                WishInterpretationResult safeResult = result;
                if (throwable != null || safeResult == null) {
                    safeResult = WishInterpretationResult.requestFailure(AiErrorCategory.UNKNOWN, 0);
                }
                if (safeResult.interpretation() != null) {
                    minecraft.setScreen(new AiTestResultScreen(this, wish, safeResult.interpretation()));
                } else {
                    status = Component.translatable(
                            "screen.wishing_willow.ai.status.error." + safeResult.errorCategory().name().toLowerCase()
                    );
                }
            });
        });
    }

    @Override
    public void tick() {
        input.tick();
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        RetroUiTheme.drawBackdrop(graphics);
        RetroUiTheme.drawPaperPanel(graphics, panelLeft, panelTop, panelWidth, panelHeight);
        graphics.drawCenteredString(font, title, width / 2, panelTop + 10, RetroUiTheme.OXBLOOD_DARK);
        graphics.drawCenteredString(font, status, width / 2, statusY, RetroUiTheme.MUTED_INK);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
