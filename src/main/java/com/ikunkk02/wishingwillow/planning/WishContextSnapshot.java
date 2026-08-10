package com.ikunkk02.wishingwillow.planning;

import java.util.List;

public record WishContextSnapshot(
        String dimension,
        long gameTime,
        String dayPhase,
        String weather,
        float health,
        float maxHealth,
        int hunger,
        int experienceLevel,
        String gameMode,
        String biome,
        int approximateY,
        String environmentType,
        String heldItem,
        List<String> armorSummary,
        List<NearbyEntitySummary> nearbyEntities,
        int nearbyHostileCount,
        int nearbyPassiveCount
) {
    public WishContextSnapshot {
        armorSummary = List.copyOf(armorSummary);
        nearbyEntities = List.copyOf(nearbyEntities).stream().limit(20).toList();
    }

    public record NearbyEntitySummary(String entityType, int count) { }
}
