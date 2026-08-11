package com.ikunkk02.wishingwillow.agent.core;

import com.ikunkk02.wishingwillow.agent.tool.ToolStatus;

public record ToolCallHistoryEntry(
        int iteration,
        String toolName,
        String normalizedArguments,
        ToolStatus status,
        String code
) { }
