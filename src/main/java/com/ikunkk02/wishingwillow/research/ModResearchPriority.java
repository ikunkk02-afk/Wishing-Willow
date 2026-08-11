package com.ikunkk02.wishingwillow.research;

import java.util.Comparator;
import java.util.Map;

public final class ModResearchPriority {
    private static final Map<ModCategory, Integer> ORDER = Map.ofEntries(
            Map.entry(ModCategory.HORROR, 0),
            Map.entry(ModCategory.MOBS, 1),
            Map.entry(ModCategory.DIMENSION, 2),
            Map.entry(ModCategory.CONTENT, 3),
            Map.entry(ModCategory.MAGIC, 4),
            Map.entry(ModCategory.COMBAT, 5),
            Map.entry(ModCategory.WORLDGEN, 6),
            Map.entry(ModCategory.TECHNOLOGY, 7),
            Map.entry(ModCategory.UTILITY, 8),
            Map.entry(ModCategory.UNKNOWN, 9),
            Map.entry(ModCategory.COSMETIC, 10),
            Map.entry(ModCategory.LIBRARY, 100),
            Map.entry(ModCategory.API, 101),
            Map.entry(ModCategory.PERFORMANCE, 102)
    );

    private ModResearchPriority() {
    }

    public static int value(ModCategory category) {
        return ORDER.getOrDefault(category, 99);
    }

    public static Comparator<KnowledgeEntry> order() {
        return Comparator.comparingInt((KnowledgeEntry entry) -> value(entry.category()))
                .thenComparing(entry -> entry.installed().modId());
    }
}
