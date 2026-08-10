package com.ikunkk02.wishingwillow.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

public final class PasswordEditBox extends EditBox {
    private boolean passwordVisible;

    public PasswordEditBox(Font font, int x, int y, int width, int height, Component narration) {
        super(font, x, y, width, height, narration);
        updateFormatter();
    }

    public void setPasswordVisible(boolean visible) {
        passwordVisible = visible;
        updateFormatter();
    }

    public boolean isPasswordVisible() {
        return passwordVisible;
    }

    @Override
    protected MutableComponent createNarrationMessage() {
        return passwordVisible
                ? super.createNarrationMessage()
                : Component.translatable("screen.wishing_willow.ai.api_key.hidden_narration");
    }

    private void updateFormatter() {
        if (passwordVisible) {
            setFormatter((text, offset) -> FormattedCharSequence.forward(text, Style.EMPTY));
        } else {
            setFormatter((text, offset) -> FormattedCharSequence.forward("•".repeat(text.length()), Style.EMPTY));
        }
    }
}
