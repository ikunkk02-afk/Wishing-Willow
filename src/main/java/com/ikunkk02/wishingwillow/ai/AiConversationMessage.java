package com.ikunkk02.wishingwillow.ai;

import java.util.List;

public record AiConversationMessage(String role, String content, List<AiToolCall> toolCalls, String toolCallId) {
    public AiConversationMessage {
        content = content == null ? "" : content;
        toolCalls = List.copyOf(toolCalls == null ? List.of() : toolCalls);
        toolCallId = toolCallId == null ? "" : toolCallId;
    }
    public static AiConversationMessage text(String role, String content) {
        return new AiConversationMessage(role, content, List.of(), "");
    }
    public static AiConversationMessage assistant(String content, List<AiToolCall> calls) {
        return new AiConversationMessage("assistant", content, calls, "");
    }
    public static AiConversationMessage tool(String id, String content) {
        return new AiConversationMessage("tool", content, List.of(), id);
    }
}
