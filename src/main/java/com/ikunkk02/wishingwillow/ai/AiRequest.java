package com.ikunkk02.wishingwillow.ai;

import com.google.gson.JsonObject;

import javax.annotation.Nullable;

public record AiRequest(
        String systemMessage,
        String userMessage,
        int maxTokens,
        AiOutputMode outputMode,
        @Nullable JsonObject jsonSchema
) {
    public AiRequest withOutputMode(AiOutputMode mode) {
        return new AiRequest(systemMessage, userMessage, maxTokens, mode, jsonSchema);
    }
}
