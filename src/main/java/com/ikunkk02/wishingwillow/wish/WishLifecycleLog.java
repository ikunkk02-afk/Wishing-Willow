package com.ikunkk02.wishingwillow.wish;

import com.ikunkk02.wishingwillow.WishingWillow;

import java.util.UUID;

/** Uniform session-scoped lifecycle logging for the client/server wish pipeline. */
public final class WishLifecycleLog {
    private WishLifecycleLog() { }

    public static void event(UUID sessionId, String event, String detail) {
        WishingWillow.LOGGER.info("Wish lifecycle session={} event={} {}",
                sessionId, safe(event), safe(detail));
    }

    public static void terminated(UUID sessionId, WishSessionTerminationReason reason,
                                  WishPipelineState lastState, long createdAt, long terminatedAt) {
        WishingWillow.LOGGER.info(
                "Session terminated session={} reason={} lastState={} createdAt={} terminatedAt={} elapsedMs={}",
                sessionId, reason, lastState, createdAt, terminatedAt,
                Math.max(0L, terminatedAt - createdAt));
    }

    private static String safe(String value) {
        if (value == null) return "";
        String clean = value.replace('\n', ' ').replace('\r', ' ').trim();
        return clean.length() > 256 ? clean.substring(0, 256) : clean;
    }
}
