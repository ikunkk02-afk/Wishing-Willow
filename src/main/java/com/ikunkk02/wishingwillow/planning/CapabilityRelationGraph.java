package com.ikunkk02.wishingwillow.planning;

import com.ikunkk02.wishingwillow.ai.WishCapability;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

public final class CapabilityRelationGraph {
    private final Map<WishCapability, Set<WishCapability>> compatible = new EnumMap<>(WishCapability.class);
    private final Map<WishCapability, Set<WishCapability>> approximate = new EnumMap<>(WishCapability.class);

    public CapabilityRelationGraph() {
        compatible.put(WishCapability.PERSISTENT_FOLLOWER,
                Set.of(WishCapability.STALKING_ENTITY, WishCapability.FRIENDLY_ENTITY));
        approximate.put(WishCapability.PERSISTENT_FOLLOWER, Set.of(WishCapability.MOB_BEHAVIOR));
        compatible.put(WishCapability.STALKING_ENTITY,
                Set.of(WishCapability.PERSISTENT_FOLLOWER, WishCapability.HOSTILE_ENTITY));
        approximate.put(WishCapability.STALKING_ENTITY, Set.of(WishCapability.MOB_BEHAVIOR));
        compatible.put(WishCapability.DIMENSION_TRAVEL, Set.of(WishCapability.TELEPORT));
        approximate.put(WishCapability.DIMENSION_TRAVEL, Set.of(WishCapability.VISUAL_EVENT));
        compatible.put(WishCapability.SPACE_TRAVEL,
                Set.of(WishCapability.DIMENSION_TRAVEL, WishCapability.TELEPORT));
        approximate.put(WishCapability.SPACE_TRAVEL, Set.of(WishCapability.VISUAL_EVENT));
        compatible.put(WishCapability.SPACECRAFT,
                Set.of(WishCapability.DIMENSION_TRAVEL, WishCapability.STRUCTURE));
        approximate.put(WishCapability.SPACECRAFT,
                Set.of(WishCapability.TELEPORT, WishCapability.VISUAL_EVENT));
        compatible.put(WishCapability.POWER_BUFF,
                Set.of(WishCapability.PLAYER_ATTRIBUTE, WishCapability.STRONG_WEAPON));
        approximate.put(WishCapability.POWER_BUFF, Set.of(WishCapability.HEALING));
        compatible.put(WishCapability.STRONG_WEAPON,
                Set.of(WishCapability.GIVE_ITEM, WishCapability.POWER_BUFF));
        compatible.put(WishCapability.POWERFUL_ENEMY,
                Set.of(WishCapability.HOSTILE_ENTITY, WishCapability.SPAWN_ENTITY));
        approximate.put(WishCapability.WORLD_EVENT,
                Set.of(WishCapability.CHANGE_WEATHER, WishCapability.CHANGE_TIME,
                        WishCapability.VISUAL_EVENT, WishCapability.SOUND_EVENT));
        compatible.put(WishCapability.DARKNESS, Set.of(WishCapability.VISUAL_EVENT));
    }

    public MatchType relation(WishCapability requested, WishCapability provided) {
        if (requested == provided) return MatchType.EXACT;
        if (compatible.getOrDefault(requested, Set.of()).contains(provided)) return MatchType.COMPATIBLE;
        if (approximate.getOrDefault(requested, Set.of()).contains(provided)) return MatchType.APPROXIMATE;
        return MatchType.UNSATISFIED;
    }
}
