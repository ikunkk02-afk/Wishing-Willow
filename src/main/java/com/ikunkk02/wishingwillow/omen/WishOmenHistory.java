package com.ikunkk02.wishingwillow.omen;

import java.util.ArrayDeque;
import java.util.UUID;

public final class WishOmenHistory {
    private final int capacity;
    private final ArrayDeque<UUID> sessions = new ArrayDeque<>();

    public WishOmenHistory(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("capacity");
        this.capacity = capacity;
    }

    public boolean accept(UUID sessionId) {
        if (sessions.contains(sessionId)) return false;
        sessions.addLast(sessionId);
        while (sessions.size() > capacity) sessions.removeFirst();
        return true;
    }

    public int size() {
        return sessions.size();
    }

    public void clear() {
        sessions.clear();
    }
}
