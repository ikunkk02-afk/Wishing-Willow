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

    public AiModelSelectionScreen(Screen parent, List<String> models, Consumer<String> selection) {
        super(Component.translatable("screen.wishing_willow.ai.models.title"));
        this.parent = parent;
        this.models = List.copyOf(models);
        this.selection = selection;
    }

    @Override
    protected void init() {
        int start = page * PAGE_SIZE;
        int end = Math.min(models.size(), start + PAGE_SIZE);
        int listWidth = Math.min(360, width - 40);
        int x = (width - listWidth) / 2;
        int y = Math.max(42, (height - PAGE_SIZE * 22 - 54) / 2);
        for (int index = start; index < end; index++) {
            String model = models.get(index);
            addRenderableWidget(Button.builder(Component.literal(model), button -> choose(model))
                    .bounds(x, y + (index - start) * 22, listWidth, 20)
                    .build());
        }
        int navigationY = Math.min(height - 28, y + PAGE_SIZE * 22 + 4);
        Button previous = addRenderableWidget(Button.builder(
                        Component.translatable("screen.wishing_willow.ai.models.previous"),
                        button -> changePage(-1)
                )
                .bounds(width / 2 - 156, navigationY, 96, 20)
                .build());
        previous.active = page > 0;
        Button next = addRenderableWidget(Button.builder(
                        Component.translatable("screen.wishing_willow.ai.models.next"),
                        button -> changePage(1)
                )
                .bounds(width / 2 - 48, navigationY, 96, 20)
                .build());
        next.active = end < models.size();
        addRenderableWidget(Button.builder(
                        Component.translatable("screen.wishing_willow.cancel"),
                        button -> onClose()
                )
                .bounds(width / 2 + 60, navigationY, 96, 20)
                .build());
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
        renderBackground(graphics);
        graphics.drawCenteredString(font, title, width / 2, 18, 0xFFFFFFFF);
        graphics.drawCenteredString(
                font,
                Component.translatable("screen.wishing_willow.ai.models.page", page + 1,
                        Math.max(1, (models.size() + PAGE_SIZE - 1) / PAGE_SIZE)),
                width / 2,
                30,
                0xFFAAA49B
        );
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
