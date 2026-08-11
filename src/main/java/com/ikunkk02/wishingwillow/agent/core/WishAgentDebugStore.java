package com.ikunkk02.wishingwillow.agent.core;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class WishAgentDebugStore {
    private static final Map<UUID, WishAgentDebugSnapshot> LATEST = new ConcurrentHashMap<>();
    private WishAgentDebugStore() { }
    public static void put(UUID playerId, WishAgentDebugSnapshot snapshot) { LATEST.put(playerId, snapshot); }
    public static WishAgentDebugSnapshot latest(UUID playerId) { return LATEST.get(playerId); }
    public static void remove(UUID playerId) { LATEST.remove(playerId); }
}
