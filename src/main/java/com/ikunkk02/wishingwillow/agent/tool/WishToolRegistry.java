package com.ikunkk02.wishingwillow.agent.tool;

import com.ikunkk02.wishingwillow.agent.core.WishAgentSession;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class WishToolRegistry {
    private final Map<String, RegisteredWishTool> tools = new LinkedHashMap<>();

    public synchronized void register(RegisteredWishTool tool) {
        if (tool == null || tool.descriptor() == null || tool.executor() == null) {
            throw new IllegalArgumentException("INVALID_TOOL");
        }
        String name = tool.descriptor().name();
        if (!name.matches("^[a-z][a-z0-9_]{1,63}$") || tools.putIfAbsent(name, tool) != null) {
            throw new IllegalArgumentException("DUPLICATE_OR_INVALID_TOOL");
        }
    }

    public synchronized void registerExternal(ExternalWishToolProvider provider) {
        if (provider == null) return;
        for (RegisteredWishTool tool : provider.tools()) if (provider.allowed(tool)) register(tool);
    }

    public synchronized RegisteredWishTool find(String name) { return tools.get(name); }
    public synchronized Collection<RegisteredWishTool> all() { return List.copyOf(tools.values()); }

    public synchronized List<RegisteredWishTool> visible(WishAgentSession session) {
        return tools.values().stream().filter(tool -> tool.descriptor().alwaysVisible()
                || session.skillActivated() && session.discoveredTools().contains(tool.descriptor().name())).toList();
    }

    public synchronized List<RegisteredWishTool> searchable(WishAgentSession session) {
        if (!session.skillActivated()) return List.of();
        return tools.values().stream().filter(tool -> !tool.descriptor().alwaysVisible()).toList();
    }
}
