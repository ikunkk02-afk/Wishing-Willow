package com.ikunkk02.wishingwillow.planning;

import com.ikunkk02.wishingwillow.ai.WishCapability;
import com.ikunkk02.wishingwillow.ai.WishInterpretation;
import com.ikunkk02.wishingwillow.contract.WishContractType;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Adds legal implementation capabilities implied by a frozen schema-v2 Wish Contract. */
public final class WishContractCapabilityDeriver {
    private WishContractCapabilityDeriver() {
    }

    public static List<WishCapability> planningCapabilities(WishInterpretation interpretation) {
        Set<WishCapability> capabilities = new LinkedHashSet<>(interpretation.requiredCapabilities());
        if (interpretation.schemaVersion() < 2) return List.copyOf(capabilities);
        capabilities.addAll(contractCapabilities(interpretation.contract().type()));
        return List.copyOf(capabilities);
    }

    public static boolean allows(WishInterpretation interpretation, WishCapability capability) {
        return planningCapabilities(interpretation).contains(capability);
    }

    private static List<WishCapability> contractCapabilities(WishContractType type) {
        List<WishCapability> result = new ArrayList<>();
        switch (type) {
            case OBTAIN_RESOURCE -> result.add(WishCapability.GIVE_ITEM);
            case CREATE_STRUCTURE -> result.add(WishCapability.STRUCTURE);
            case CHANGE_PLAYER_STATE, PERSISTENT_CONDITION ->
                    result.add(WishCapability.PLAYER_ATTRIBUTE);
            case SOCIAL_RELATION -> result.add(WishCapability.REPUTATION);
            case SPAWN_COMPANION -> {
                result.add(WishCapability.FRIENDLY_ENTITY);
                result.add(WishCapability.PERSISTENT_FOLLOWER);
            }
            case TRAVEL -> result.add(WishCapability.TELEPORT);
            case CHANGE_WORLD_STATE -> result.add(WishCapability.WORLD_EVENT);
            case REMOVE_THREAT -> result.add(WishCapability.MOB_BEHAVIOR);
            case KNOWLEDGE, RESURRECTION, OTHER -> {
                // These contracts require semantic review and do not imply one safe primitive.
            }
        }
        return result;
    }
}
