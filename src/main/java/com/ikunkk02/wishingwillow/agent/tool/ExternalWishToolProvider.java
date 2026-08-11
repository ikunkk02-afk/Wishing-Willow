package com.ikunkk02.wishingwillow.agent.tool;

import java.util.List;

/** Future MCP boundary. Providers may only contribute read-only discovery or proposal tools. */
public interface ExternalWishToolProvider {
    List<RegisteredWishTool> tools();

    default boolean allowed(RegisteredWishTool tool) {
        if (tool == null || tool.descriptor() == null) return false;
        String name = tool.descriptor().name().toLowerCase(java.util.Locale.ROOT);
        if (List.of("shell", "powershell", "cmd", "bash", "filesystem_write",
                "execute_code", "download_and_execute").stream().anyMatch(name::contains)) return false;
        return tool.descriptor().readOnly()
                && tool.descriptor().category() == WishToolCategory.DISCOVERY;
    }
}
