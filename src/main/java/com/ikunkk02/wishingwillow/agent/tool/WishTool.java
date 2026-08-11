package com.ikunkk02.wishingwillow.agent.tool;

import com.google.gson.JsonObject;
import com.ikunkk02.wishingwillow.agent.core.WishAgentSession;

@FunctionalInterface
public interface WishTool {
    ToolResult execute(WishAgentSession session, JsonObject arguments);
}
