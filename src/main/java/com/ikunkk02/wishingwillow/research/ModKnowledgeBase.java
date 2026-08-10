package com.ikunkk02.wishingwillow.research;

import com.ikunkk02.wishingwillow.ai.WishCapability;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ModKnowledgeBase {
    private final Map<String, KnowledgeEntry> entries = new ConcurrentHashMap<>();
    private volatile KnowledgeBaseState state = KnowledgeBaseState.NOT_STARTED;
    private volatile boolean paused;

    public KnowledgeBaseSnapshot snapshot() {
        List<KnowledgeEntry> copy = new ArrayList<>(entries.values());
        copy.sort(Comparator.comparing((KnowledgeEntry entry) -> priority(entry.category()))
                .thenComparing(entry -> entry.installed().displayName(), String.CASE_INSENSITIVE_ORDER));
        return new KnowledgeBaseSnapshot(state, paused, copy);
    }

    @Nullable
    public KnowledgeEntry findMod(String modId) {
        return entries.get(modId);
    }

    public List<KnowledgeEntry> findByCapability(WishCapability capability) {
        return entries.values().stream()
                .filter(entry -> entry.knowledge() != null
                        && entry.knowledge().availableCapabilities().contains(capability))
                .sorted(Comparator.comparing(entry -> entry.installed().modId()))
                .toList();
    }

    void replaceAll(List<KnowledgeEntry> values) {
        entries.clear();
        values.forEach(entry -> entries.put(entry.installed().modId(), entry));
    }

    void put(KnowledgeEntry entry) {
        entries.put(entry.installed().modId(), entry);
    }

    void setState(KnowledgeBaseState state) {
        this.state = state;
    }

    void setPaused(boolean paused) {
        this.paused = paused;
    }

    private static int priority(ModCategory category) {
        return switch (category) {
            case HORROR -> 0;
            case MOBS, DIMENSION, CONTENT -> 1;
            case MAGIC, COMBAT, WORLDGEN -> 2;
            case TECHNOLOGY, UTILITY, COSMETIC, UNKNOWN -> 3;
            case LIBRARY, PERFORMANCE, API -> 4;
        };
    }
}
