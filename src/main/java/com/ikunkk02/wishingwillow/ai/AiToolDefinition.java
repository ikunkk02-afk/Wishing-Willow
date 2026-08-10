package com.ikunkk02.wishingwillow.ai;

import com.google.gson.JsonObject;

public record AiToolDefinition(String name, String description, JsonObject parameters) {
    public AiToolDefinition {
        parameters = parameters == null ? new JsonObject() : parameters.deepCopy();
    }
}
