package com.ikunkk02.wishingwillow.client.gui;

import com.ikunkk02.wishingwillow.execution.ExecutionSettingsSnapshot;
import com.ikunkk02.wishingwillow.network.ModNetworking;
import com.ikunkk02.wishingwillow.network.packet.RequestExecutionSettingsPacket;
import com.ikunkk02.wishingwillow.network.packet.UpdateExecutionSettingsPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class ExecutionSettingsScreen extends Screen {
    private final Screen parent;
    private ExecutionSettingsSnapshot value = new ExecutionSettingsSnapshot(
            true, true, true, true, false, false, true, 80, false);
    private int scrollRow;
    private int panelX;
    private int panelWidth;
    private int contentTop;
    private int contentBottom;

    public ExecutionSettingsScreen(Screen parent) {
        super(Component.translatable("screen.wishing_willow.execution.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        rebuild();
        ModNetworking.sendToServer(new RequestExecutionSettingsPacket());
    }

    public void apply(ExecutionSettingsSnapshot settings) {
        value = settings;
        if (minecraft != null) minecraft.execute(this::rebuild);
    }

    private void rebuild() {
        clearWidgets();
        panelWidth = Math.min(620, Math.max(190, width - 12));
        panelX = (width - panelWidth) / 2;
        boolean warning = value.debugSafeMode();
        contentTop = warning && height >= 150 ? 72 : 39;
        contentBottom = height - 34;
        int columns = width < 420 ? 1 : 2;
        int totalRows = (8 + columns - 1) / columns;
        int visibleRows = Math.max(1, (contentBottom - contentTop) / 25);
        scrollRow = Math.max(0, Math.min(scrollRow, Math.max(0, totalRows - visibleRows)));
        int gap = 5;
        int buttonWidth = (panelWidth - 20 - gap * (columns - 1)) / columns;
        for (int index = 0; index < 8; index++) {
            int row = index / columns;
            if (row < scrollRow || row >= scrollRow + visibleRows) continue;
            int column = index % columns;
            int x = panelX + 10 + column * (buttonWidth + gap);
            int y = contentTop + (row - scrollRow) * 25;
            addSetting(index, x, y, buttonWidth);
        }
        int footerY = height - 27;
        int footerWidth = Math.min(150, (panelWidth - 25) / 2);
        RetroButton save = addRenderableWidget(RetroButton.create(
                Component.translatable("screen.wishing_willow.execution.save"),
                button -> ModNetworking.sendToServer(new UpdateExecutionSettingsPacket(value)),
                width / 2 - footerWidth - 3, footerY, footerWidth, 20));
        save.active = value.canEdit();
        addRenderableWidget(RetroButton.create(Component.translatable("gui.done"), button -> onClose(),
                width / 2 + 3, footerY, footerWidth, 20));
    }

    private void addSetting(int index, int x, int y, int buttonWidth) {
        if (index == 7) {
            RetroButton severity = addRenderableWidget(RetroButton.create(label(
                    "screen.wishing_willow.execution.max_severity",
                    Integer.toString(value.maximumDestructiveSeverity())), button -> {
                value = new ExecutionSettingsSnapshot(value.enabled(), value.thirdPartyEntities(),
                        value.blockModification(), value.explosions(), value.destructiveExplosions(),
                        value.crossDimensionTeleport(), value.debugSafeMode(),
                        (value.maximumDestructiveSeverity() + 20) % 120, value.canEdit());
                rebuild();
            }, x, y, buttonWidth, 20));
            severity.active = value.canEdit();
            severity.setTooltip(Tooltip.create(Component.translatable("screen.wishing_willow.execution.warning.severity")));
            return;
        }
        String key = switch (index) {
            case 0 -> "enabled";
            case 1 -> "third_party";
            case 2 -> "blocks";
            case 3 -> "explosions";
            case 4 -> "destructive";
            case 5 -> "cross_dimension";
            default -> "safe_mode";
        };
        boolean danger = index >= 2 && index <= 5;
        Component name = Component.translatable("screen.wishing_willow.execution." + key);
        if (danger) name = name.copy().append(" ⚠");
        Component buttonLabel = name.copy().append(": ").append(Component.translatable(
                get(index) ? "options.on" : "options.off"));
        RetroButton toggle = addRenderableWidget(RetroButton.create(buttonLabel, button -> {
            set(index, !get(index));
            rebuild();
        }, x, y, buttonWidth, 20));
        toggle.active = value.canEdit();
        if (danger) toggle.setTooltip(Tooltip.create(Component.translatable(
                "screen.wishing_willow.execution.warning." + key)));
    }

    private Component label(String key, String status) {
        return Component.translatable(key).append(": ").append(status);
    }

    private boolean get(int index) {
        return switch (index) {
            case 0 -> value.enabled();
            case 1 -> value.thirdPartyEntities();
            case 2 -> value.blockModification();
            case 3 -> value.explosions();
            case 4 -> value.destructiveExplosions();
            case 5 -> value.crossDimensionTeleport();
            default -> value.debugSafeMode();
        };
    }

    private void set(int index, boolean enabled) {
        value = new ExecutionSettingsSnapshot(index == 0 ? enabled : value.enabled(),
                index == 1 ? enabled : value.thirdPartyEntities(),
                index == 2 ? enabled : value.blockModification(),
                index == 3 ? enabled : value.explosions(),
                index == 4 ? enabled : value.destructiveExplosions(),
                index == 5 ? enabled : value.crossDimensionTeleport(),
                index == 6 ? enabled : value.debugSafeMode(), value.maximumDestructiveSeverity(), value.canEdit());
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int columns = width < 420 ? 1 : 2;
        int totalRows = (8 + columns - 1) / columns;
        int visibleRows = Math.max(1, (contentBottom - contentTop) / 25);
        int maximum = Math.max(0, totalRows - visibleRows);
        if (maximum > 0) {
            scrollRow = Math.max(0, Math.min(maximum, scrollRow - (int) Math.signum(delta)));
            rebuild();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        RetroUiTheme.drawBackdrop(graphics);
        RetroUiTheme.drawPaperPanel(graphics, panelX, 3, panelWidth, height - 7);
        graphics.drawCenteredString(font, title, width / 2, 11, RetroUiTheme.OXBLOOD_DARK);
        if (value.debugSafeMode() && height >= 150) {
            RetroUiTheme.drawWarningBar(graphics, font,
                    Component.translatable("screen.wishing_willow.execution.safe_mode_active"),
                    Component.translatable("screen.wishing_willow.execution.safe_mode_warning_1"),
                    panelX + 10, 32, panelWidth - 20);
        }
        if (!value.canEdit()) {
            graphics.drawCenteredString(font, Component.translatable("screen.wishing_willow.execution.read_only"),
                    width / 2, height >= 150 ? 22 : 25, RetroUiTheme.STATUS_WARN);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
