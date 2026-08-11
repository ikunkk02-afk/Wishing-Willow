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
import com.ikunkk02.wishingwillow.client.planning.PlanningDebugController;

public final class AiTestResultScreen extends Screen {
    private final Screen parent;
    private final String wish;
    private final WishInterpretation interpretation;
    private int scroll;
    private int maxScroll;

    public AiTestResultScreen(Screen parent, String wish, WishInterpretation interpretation) {
        super(Component.translatable("screen.wishing_willow.ai.test.result.title"));
        this.parent = parent;
        this.wish = wish;
        this.interpretation = interpretation;
    }

    @Override
    protected void init() {
        int buttonWidth = Math.min(100, (Math.max(190, width - 20) - 18) / 2);
        addRenderableWidget(RetroButton.create(Component.translatable("screen.wishing_willow.ai.test.result.plan"),
                button -> PlanningDebugController.run(this, wish, interpretation),
                width / 2 - buttonWidth - 3, height - 24, buttonWidth, 20));
        addRenderableWidget(RetroButton.create(Component.translatable("screen.wishing_willow.ai.test.result.back"),
                button -> onClose(), width / 2 + 3, height - 24, buttonWidth, 20));
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
        int panelWidth = Math.min(560, Math.max(190, width - 12));
        RetroUiTheme.drawPaperPanel(graphics, (width - panelWidth) / 2, 3, panelWidth, height - 7);
        graphics.drawCenteredString(font, title, width / 2, 10, RetroUiTheme.OXBLOOD_DARK);
        int maxWidth = Math.min(520, width - 40);
        int x = (width - maxWidth) / 2;
        int y = 29;
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
        List<FormattedCharSequence> lines = new ArrayList<>();
        for (Component section : sections) {
            for (FormattedCharSequence line : font.split(section, maxWidth)) {
                lines.add(line);
            }
            lines.add(FormattedCharSequence.EMPTY);
        }
        int visible = Math.max(1, (height - 61) / 10);
        maxScroll = Math.max(0, lines.size() - visible);
        scroll = Math.max(0, Math.min(scroll, maxScroll));
        graphics.enableScissor(x, 27, x + maxWidth, height - 28);
        for (int index = scroll; index < lines.size() && y < height - 30; index++, y += 10) {
            graphics.drawString(font, lines.get(index), x, y, RetroUiTheme.INK);
        }
        graphics.disableScissor();
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (maxScroll > 0) {
            scroll = Math.max(0, Math.min(maxScroll, scroll - (int) Math.signum(delta)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
