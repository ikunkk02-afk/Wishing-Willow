package com.ikunkk02.wishingwillow.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

public final class RetroUiTheme {
    public static final int CREAM = 0xFFF0E2BF;
    public static final int CREAM_DARK = 0xFFE0CDA4;
    public static final int PAPER_SHADOW = 0xFF5A392D;
    public static final int OXBLOOD = 0xFF7C2426;
    public static final int OXBLOOD_DARK = 0xFF511A1C;
    public static final int INK = 0xFF36251E;
    public static final int MUTED_INK = 0xFF72584A;
    public static final int STATUS_OK = 0xFF60734E;
    public static final int STATUS_WARN = 0xFF966B32;

    private RetroUiTheme() {
    }

    public static void drawBackdrop(GuiGraphics graphics) {
        graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), 0x74170D0B);
        for (int y = 3; y < graphics.guiHeight(); y += 8) {
            graphics.fill(0, y, graphics.guiWidth(), y + 1, 0x08000000);
        }
    }

    public static void drawPaperPanel(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x + 3, y + 4, x + width + 3, y + height + 4, 0x70000000);
        graphics.fill(x, y, x + width, y + height, CREAM);
        graphics.fill(x + 3, y + 3, x + width - 3, y + height - 3, 0x18A5663C);
        graphics.renderOutline(x, y, width, height, OXBLOOD_DARK);
        graphics.renderOutline(x + 2, y + 2, width - 4, height - 4, OXBLOOD);
        for (int row = y + 7; row < y + height - 5; row += 13) {
            for (int col = x + 7 + ((row / 13) & 1) * 5; col < x + width - 5; col += 23) {
                graphics.fill(col, row, col + 1, row + 1, 0x26705A3A);
            }
        }
        drawStar(graphics, x + 7, y + 7);
        drawStar(graphics, x + width - 8, y + 7);
        drawStar(graphics, x + 7, y + height - 8);
        drawStar(graphics, x + width - 8, y + height - 8);
    }

    public static void drawHeader(GuiGraphics graphics, Font font, Component title, int centerX, int y) {
        int width = font.width(title);
        graphics.fill(centerX - width / 2 - 12, y - 3, centerX + width / 2 + 12, y + 11, OXBLOOD);
        graphics.drawCenteredString(font, title, centerX, y, CREAM);
    }

    public static void drawStatusBadge(GuiGraphics graphics, Font font, Component text,
                                       int centerX, int y, boolean positive) {
        int textWidth = font.width(text);
        int color = positive ? STATUS_OK : STATUS_WARN;
        graphics.fill(centerX - textWidth / 2 - 9, y - 3, centerX + textWidth / 2 + 9, y + 11,
                0xFFE8D7B2);
        graphics.renderOutline(centerX - textWidth / 2 - 9, y - 3, textWidth + 18, 14, color);
        graphics.fill(centerX - textWidth / 2 - 5, y + 2, centerX - textWidth / 2 - 2, y + 5, color);
        graphics.drawString(font, text, centerX - textWidth / 2 + 1, y, INK, false);
    }

    public static void drawWarningBar(GuiGraphics graphics, Font font, Component line1, Component line2,
                                      int x, int y, int width) {
        graphics.fill(x, y, x + width, y + 28, 0xFFE7D2A4);
        graphics.renderOutline(x, y, width, 28, STATUS_WARN);
        graphics.drawCenteredString(font, line1, x + width / 2, y + 5, OXBLOOD_DARK);
        graphics.drawCenteredString(font, line2, x + width / 2, y + 16, MUTED_INK);
    }

    private static void drawStar(GuiGraphics graphics, int x, int y) {
        graphics.fill(x - 2, y, x + 3, y + 1, OXBLOOD);
        graphics.fill(x, y - 2, x + 1, y + 3, OXBLOOD);
    }
}
