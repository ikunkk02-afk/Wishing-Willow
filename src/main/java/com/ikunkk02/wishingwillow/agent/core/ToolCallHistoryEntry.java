package com.ikunkk02.wishingwillow.agent.core;

import com.ikunkk02.wishingwillow.agent.tool.ToolStatus;

public record ToolCallHistoryEntry(
        int iteration,
        String toolName,
        String normalizedArguments,
        ToolStatus status,
        String code,
        String why
) {
    public ToolCallHistoryEntry {
        why = why == null || why.isBlank() ? "not_provided" : why.strip();
    }

    public ToolCallHistoryEntry(int iteration, String toolName, String normalizedArguments,
                                ToolStatus status, String code) {
        this(iteration, toolName, normalizedArguments, status, code, "not_provided");
    }
}
