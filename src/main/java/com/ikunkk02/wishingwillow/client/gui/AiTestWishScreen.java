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

    public AiTestWishScreen(Screen parent, AiConfig config) {
        super(Component.translatable("screen.wishing_willow.ai.test.title"));
        this.parent = parent;
        this.config = config;
    }

    @Override
    protected void init() {
        int panelWidth = Math.min(400, width - 32);
        int left = (width - panelWidth) / 2;
        int top = Math.max(28, height / 2 - 105);
        input = new MultiLineEditBox(
                font, left + 12, top + 34, panelWidth - 24, 88,
                Component.translatable("screen.wishing_willow.ai.test.placeholder"),
                Component.translatable("screen.wishing_willow.ai.test.input")
        );
        input.setCharacterLimit(WishTextValidator.MAX_LENGTH);
        input.setValue(Component.translatable("screen.wishing_willow.ai.test.example").getString());
        input.setValueListener(value -> testButton.active = !running && !value.strip().isEmpty());
        addRenderableWidget(input);
        testButton = addRenderableWidget(Button.builder(
                        Component.translatable("screen.wishing_willow.ai.test.run"),
                        button -> runTest()
                )
                .bounds(width / 2 - 106, top + 152, 100, 20)
                .build());
        addRenderableWidget(Button.builder(
                        Component.translatable("screen.wishing_willow.cancel"),
                        button -> onClose()
                )
                .bounds(width / 2 + 6, top + 152, 100, 20)
                .build());
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
        renderBackground(graphics);
        int top = Math.max(28, height / 2 - 105);
        graphics.drawCenteredString(font, title, width / 2, top + 8, 0xFFFFFFFF);
        graphics.drawCenteredString(font, status, width / 2, top + 130, 0xFFBDB7AF);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
