package com.ikunkk02.wishingwillow.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

public final class RetroButton extends Button {
    private boolean pressed;

    public RetroButton(int x, int y, int width, int height, Component message, OnPress onPress) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
    }

    public static RetroButton create(Component message, OnPress onPress,
                                     int x, int y, int width, int height) {
        return new RetroButton(x, y, width, height, message, onPress);
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int yOffset = pressed ? 1 : 0;
        int fill = !active ? 0xFFBBAF91 : isHoveredOrFocused() ? 0xFFD8BC8E : RetroUiTheme.CREAM_DARK;
        int border = !active ? 0xFF756B58 : RetroUiTheme.OXBLOOD_DARK;
        graphics.fill(getX() + 1, getY() + 2 + yOffset, getX() + width + 1,
                getY() + height + 2 + yOffset, 0x66000000);
        graphics.fill(getX(), getY() + yOffset, getX() + width, getY() + height + yOffset, fill);
        graphics.renderOutline(getX(), getY() + yOffset, width, height, border);
        if (active && isHoveredOrFocused()) {
            graphics.renderOutline(getX() + 2, getY() + 2 + yOffset, width - 4, height - 4,
                    RetroUiTheme.OXBLOOD);
        }
        int textColor = active ? RetroUiTheme.INK : 0xFF786F5E;
        graphics.drawCenteredString(Minecraft.getInstance().font, getMessage(),
                getX() + width / 2, getY() + (height - 8) / 2 + yOffset, textColor);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        pressed = active && visible && button == 0 && isMouseOver(mouseX, mouseY);
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        pressed = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }
}
