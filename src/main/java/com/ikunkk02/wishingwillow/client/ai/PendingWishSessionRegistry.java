package com.ikunkk02.wishingwillow.client.ai;

import com.ikunkk02.wishingwillow.wish.WishSessionTerminationReason;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Atomic owner/removal boundary for client wish pipeline sessions. */
final class PendingWishSessionRegistry {
    private final ConcurrentMap<UUID, PendingWishSession> sessions = new ConcurrentHashMap<>();

    @Nullable
    PendingWishSession register(PendingWishSession session) {
        return sessions.put(session.sessionId(), session);
    }

    @Nullable
    PendingWishSession get(UUID sessionId) {
        return sessions.get(sessionId);
    }

    boolean contains(UUID sessionId) {
        return sessions.containsKey(sessionId);
    }

    @Nullable
    PendingWishSession terminate(UUID sessionId, WishSessionTerminationReason reason, long now) {
        final PendingWishSession[] terminated = new PendingWishSession[1];
        sessions.computeIfPresent(sessionId, (ignored, session) -> {
            if (!session.terminate(reason, now)) return session;
            terminated[0] = session;
            return null;
        });
        return terminated[0];
    }

    List<PendingWishSession> terminateAll(WishSessionTerminationReason reason, long now) {
        List<PendingWishSession> terminated = new ArrayList<>();
        for (UUID sessionId : List.copyOf(sessions.keySet())) {
            PendingWishSession session = terminate(sessionId, reason, now);
            if (session != null) terminated.add(session);
        }
        return List.copyOf(terminated);
    }

    int size() {
        return sessions.size();
    }

    List<PendingWishSession> sessions() {
        return List.copyOf(sessions.values());
    }
}
