package com.ikunkk02.wishingwillow.planning;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/** Cancellation/generation gate shared by every asynchronous stage of client planning. */
public final class WishPlanningGeneration {
    private final AtomicLong generation = new AtomicLong();
    private final AtomicReference<CompletableFuture<?>> active = new AtomicReference<>();

    public Token begin(UUID sessionId) {
        long value = generation.incrementAndGet();
        CompletableFuture<?> previous = active.getAndSet(null);
        if (previous != null) previous.cancel(true);
        return new Token(this, value, sessionId);
    }

    public void track(Token token, CompletableFuture<?> future) {
        if (!isCurrent(token)) {
            future.cancel(true);
            return;
        }
        CompletableFuture<?> previous = active.getAndSet(future);
        if (previous != null && previous != future) previous.cancel(true);
        future.whenComplete((ignored, error) -> active.compareAndSet(future, null));
    }

    public boolean isCurrent(Token token) {
        return token != null && token.owner == this && token.generation == generation.get();
    }

    public void cancelAll() {
        generation.incrementAndGet();
        CompletableFuture<?> previous = active.getAndSet(null);
        if (previous != null) previous.cancel(true);
    }

    public static final class Token {
        private final WishPlanningGeneration owner;
        private final long generation;
        private final UUID sessionId;

        private Token(WishPlanningGeneration owner, long generation, UUID sessionId) {
            this.owner = owner;
            this.generation = generation;
            this.sessionId = sessionId;
        }

        public long generation() { return generation; }
        public UUID sessionId() { return sessionId; }
        public boolean cancelled() { return !owner.isCurrent(this); }
    }
}
