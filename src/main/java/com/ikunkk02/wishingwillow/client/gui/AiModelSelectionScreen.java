package com.ikunkk02.wishingwillow.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;

public final class AiModelSelectionScreen extends Screen {
    private static final int PAGE_SIZE = 8;

    private final Screen parent;
    private final List<String> models;
    private final Consumer<String> selection;
    private int page;
    private int pageSize = PAGE_SIZE;

    public AiModelSelectionScreen(Screen parent, List<String> models, Consumer<String> selection) {
        super(Component.translatable("screen.wishing_willow.ai.models.title"));
        this.parent = parent;
        this.models = List.copyOf(models);
        this.selection = selection;
    }

    @Override
    protected void init() {
        pageSize = Math.max(1, Math.min(PAGE_SIZE, (height - 68) / 22));
        int pages = Math.max(1, (models.size() + pageSize - 1) / pageSize);
        page = Math.max(0, Math.min(page, pages - 1));
        int start = page * pageSize;
        int end = Math.min(models.size(), start + pageSize);
        int listWidth = Math.min(360, width - 40);
        int x = (width - listWidth) / 2;
        int y = Math.max(28, (height - pageSize * 22 - 44) / 2);
        for (int index = start; index < end; index++) {
            String model = models.get(index);
            addRenderableWidget(RetroButton.create(Component.literal(model), button -> choose(model),
                    x, y + (index - start) * 22, listWidth, 20));
        }
        int navigationY = Math.min(height - 24, y + pageSize * 22 + 2);
        int navWidth = Math.min(96, (listWidth - 12) / 3);
        Button previous = addRenderableWidget(RetroButton.create(
                Component.translatable("screen.wishing_willow.ai.models.previous"), button -> changePage(-1),
                width / 2 - navWidth - navWidth / 2 - 4, navigationY, navWidth, 20));
        previous.active = page > 0;
        Button next = addRenderableWidget(RetroButton.create(
                Component.translatable("screen.wishing_willow.ai.models.next"), button -> changePage(1),
                width / 2 - navWidth / 2, navigationY, navWidth, 20));
        next.active = end < models.size();
        addRenderableWidget(RetroButton.create(Component.translatable("screen.wishing_willow.cancel"),
                button -> onClose(), width / 2 + navWidth / 2 + 4, navigationY, navWidth, 20));
    }

    private void choose(String model) {
        selection.accept(model);
        onClose();
    }

    private void changePage(int direction) {
        page += direction;
        rebuildWidgets();
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
        int panelWidth = Math.min(410, Math.max(190, width - 12));
        RetroUiTheme.drawPaperPanel(graphics, (width - panelWidth) / 2, 3, panelWidth, height - 7);
        graphics.drawCenteredString(font, title, width / 2, 8, RetroUiTheme.OXBLOOD_DARK);
        graphics.drawCenteredString(
                font,
                Component.translatable("screen.wishing_willow.ai.models.page", page + 1,
                        Math.max(1, (models.size() + pageSize - 1) / pageSize)),
                width / 2,
                18,
                RetroUiTheme.MUTED_INK
        );
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
