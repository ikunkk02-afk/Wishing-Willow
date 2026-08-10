package com.ikunkk02.wishingwillow.ai;

import java.util.List;

public record AiToolResponse(String assistantContent, List<AiToolCall> toolCalls, int httpStatus) {
    public AiToolResponse {
        assistantContent = assistantContent == null ? "" : assistantContent;
        toolCalls = List.copyOf(toolCalls == null ? List.of() : toolCalls);
    }
}
