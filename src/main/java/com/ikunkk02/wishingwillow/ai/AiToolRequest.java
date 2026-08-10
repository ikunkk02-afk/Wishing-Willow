package com.ikunkk02.wishingwillow.ai;

import java.util.List;

public record AiToolRequest(List<AiConversationMessage> messages, List<AiToolDefinition> tools, int maxTokens) {
    public AiToolRequest {
        messages = List.copyOf(messages); tools = List.copyOf(tools);
    }
}
