package com.ikunkk02.wishingwillow.planning;

import com.ikunkk02.wishingwillow.ai.WishCapability;
import com.ikunkk02.wishingwillow.research.FeatureType;

import java.util.Set;

public enum WishingWillowBuiltinCapability {
    GIVE_RESOURCE(WishCapability.GIVE_ITEM, FeatureType.ITEM, Set.of(WishActionType.GIVE_ITEM)),
    PLACE_RESOURCE(WishCapability.BLOCK_CHANGE, FeatureType.BLOCK, Set.of(WishActionType.CHANGE_BLOCK,
            WishActionType.PLACE_BLOCK_PATTERN)),
    SPAWN_BASIC_ENTITY(WishCapability.FRIENDLY_ENTITY, FeatureType.ENTITY,
            Set.of(WishActionType.SPAWN_ENTITY, WishActionType.FOLLOW_PLAYER)),
    APPLY_PLAYER_STATE(WishCapability.HEALING, FeatureType.PLAYER_SYSTEM,
            Set.of(WishActionType.MODIFY_HEALTH)),
    MODIFY_ATTRIBUTE(WishCapability.PLAYER_ATTRIBUTE, FeatureType.PLAYER_SYSTEM,
            Set.of(WishActionType.MODIFY_ATTRIBUTE, WishActionType.MODIFY_HUNGER)),
    TELEPORT_SAFE(WishCapability.TELEPORT, FeatureType.WORLD_SYSTEM, Set.of(WishActionType.TELEPORT)),
    CHANGE_TIME(WishCapability.CHANGE_TIME, FeatureType.WORLD_SYSTEM, Set.of(WishActionType.CHANGE_TIME)),
    CHANGE_WEATHER(WishCapability.CHANGE_WEATHER, FeatureType.WEATHER, Set.of(WishActionType.CHANGE_WEATHER)),
    PLAY_SOUND(WishCapability.SOUND_EVENT, FeatureType.SOUND, Set.of(WishActionType.PLAY_SOUND)),
    SPAWN_PARTICLE(WishCapability.VISUAL_EVENT, FeatureType.WORLD_SYSTEM, Set.of(WishActionType.SPAWN_PARTICLE)),
    FOLLOW_BEHAVIOR(WishCapability.PERSISTENT_FOLLOWER, FeatureType.ENTITY,
            Set.of(WishActionType.SPAWN_ENTITY, WishActionType.FOLLOW_PLAYER)),
    SOCIAL_REPUTATION(WishCapability.REPUTATION, FeatureType.PLAYER_SYSTEM, Set.of(WishActionType.CHANGE_REPUTATION)),
    CREATE_SIMPLE_STRUCTURE(WishCapability.STRUCTURE, FeatureType.STRUCTURE, Set.of(WishActionType.CREATE_STRUCTURE)),
    WORLD_EVENT(WishCapability.WORLD_EVENT, FeatureType.WORLD_SYSTEM, Set.of(WishActionType.START_PREDEFINED_EVENT));

    private final WishCapability providedCapability;
    private final FeatureType featureType;
    private final Set<WishActionType> actions;

    WishingWillowBuiltinCapability(WishCapability providedCapability, FeatureType featureType,
                                   Set<WishActionType> actions) {
        this.providedCapability = providedCapability;
        this.featureType = featureType;
        this.actions = Set.copyOf(actions);
    }

    public WishCapability providedCapability() {
        return providedCapability;
    }

    public FeatureType featureType() {
        return featureType;
    }

    public boolean supports(WishActionType action) {
        return actions.contains(action);
    }

    public Set<WishCapability> providedCapabilities() {
        return switch (this) {
            case GIVE_RESOURCE -> Set.of(WishCapability.GIVE_ITEM, WishCapability.INVENTORY_CHANGE,
                    WishCapability.STRONG_WEAPON);
            case PLACE_RESOURCE -> Set.of(WishCapability.BLOCK_CHANGE, WishCapability.STRUCTURE);
            case SPAWN_BASIC_ENTITY -> Set.of(WishCapability.SPAWN_ENTITY, WishCapability.FRIENDLY_ENTITY);
            case APPLY_PLAYER_STATE -> Set.of(WishCapability.HEALING, WishCapability.DAMAGE,
                    WishCapability.IMMORTALITY, WishCapability.POWER_BUFF, WishCapability.POWER_DEBUFF);
            case MODIFY_ATTRIBUTE -> Set.of(WishCapability.PLAYER_ATTRIBUTE, WishCapability.POWER_BUFF,
                    WishCapability.POWER_DEBUFF);
            case FOLLOW_BEHAVIOR -> Set.of(WishCapability.PERSISTENT_FOLLOWER, WishCapability.MOB_BEHAVIOR,
                    WishCapability.STALKING_ENTITY, WishCapability.FRIENDLY_ENTITY);
            default -> Set.of(providedCapability);
        };
    }

    public static boolean isTrusted(CandidateReference candidate) {
        if (candidate == null || candidate.sourceKind() != CandidateSourceKind.WISHING_WILLOW_BUILTIN
                || !"wishing_willow".equals(candidate.sourceModId())) return false;
        try {
            WishingWillowBuiltinCapability builtin = valueOf(candidate.featureName());
            return builtin.providedCapabilities().contains(candidate.providedCapability());
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    public static boolean supports(CandidateReference candidate, WishActionType action) {
        if (!isTrusted(candidate)) return false;
        return valueOf(candidate.featureName()).supports(action);
    }
}
