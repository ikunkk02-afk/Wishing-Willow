package com.ikunkk02.wishingwillow.ai;

import com.google.gson.JsonObject;

import javax.annotation.Nullable;

public record AiRequest(
        String systemMessage,
        String userMessage,
        int maxTokens,
        AiOutputMode outputMode,
        @Nullable JsonObject jsonSchema,
        @Nullable String jsonSchemaName
) {
    public AiRequest(String systemMessage, String userMessage, int maxTokens,
                     AiOutputMode outputMode, @Nullable JsonObject jsonSchema) {
        this(systemMessage, userMessage, maxTokens, outputMode, jsonSchema,
                jsonSchema == null ? null : "wish_interpretation");
    }

    public AiRequest withOutputMode(AiOutputMode mode) {
        return new AiRequest(systemMessage, userMessage, maxTokens, mode, jsonSchema, jsonSchemaName);
    }
}
