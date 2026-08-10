package com.ikunkk02.wishingwillow.client.gui;

import com.ikunkk02.wishingwillow.ai.WishInterpretation;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public final class AiTestResultScreen extends Screen {
    private final Screen parent;
    private final String wish;
    private final WishInterpretation interpretation;

    public AiTestResultScreen(Screen parent, String wish, WishInterpretation interpretation) {
        super(Component.translatable("screen.wishing_willow.ai.test.result.title"));
        this.parent = parent;
        this.wish = wish;
        this.interpretation = interpretation;
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(
                        Component.translatable("screen.wishing_willow.ai.test.result.back"),
                        button -> onClose()
                )
                .bounds(width / 2 - 50, height - 28, 100, 20)
                .build());
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
        graphics.drawCenteredString(font, title, width / 2, 12, 0xFFFFFFFF);
        int maxWidth = Math.min(520, width - 40);
        int x = (width - maxWidth) / 2;
        int y = 32;
        List<Component> sections = new ArrayList<>();
        sections.add(Component.translatable("screen.wishing_willow.ai.test.result.wish", wish));
        sections.add(Component.translatable("screen.wishing_willow.ai.test.result.outcome", interpretation.twistedOutcome()));
        sections.add(Component.translatable("screen.wishing_willow.ai.test.result.loophole", interpretation.loophole()));
        sections.add(Component.translatable("screen.wishing_willow.ai.test.result.tone", interpretation.tone().name()));
        sections.add(Component.translatable("screen.wishing_willow.ai.test.result.severity", interpretation.severity()));
        sections.add(Component.translatable("screen.wishing_willow.ai.test.result.delivery", interpretation.delivery().name()));
        sections.add(Component.translatable(
                "screen.wishing_willow.ai.test.result.capabilities",
                interpretation.requiredCapabilities().stream().map(Enum::name).collect(Collectors.joining(", "))
        ));
        for (Component section : sections) {
            for (FormattedCharSequence line : font.split(section, maxWidth)) {
                if (y > height - 42) {
                    break;
                }
                graphics.drawString(font, line, x, y, 0xFFD6D2CB);
                y += 10;
            }
            y += 5;
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
