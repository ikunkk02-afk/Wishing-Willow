package com.ikunkk02.wishingwillow.planning;

import com.ikunkk02.wishingwillow.ai.WishCapability;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CapabilityRelationGraphTest {
    private final CapabilityRelationGraph graph=new CapabilityRelationGraph();

    @Test void distinguishesExactCompatibleApproximateAndUnsatisfied(){
        assertEquals(MatchType.EXACT,graph.relation(WishCapability.STALKING_ENTITY,WishCapability.STALKING_ENTITY));
        assertEquals(MatchType.COMPATIBLE,graph.relation(WishCapability.PERSISTENT_FOLLOWER,WishCapability.STALKING_ENTITY));
        assertEquals(MatchType.APPROXIMATE,graph.relation(WishCapability.SPACECRAFT,WishCapability.TELEPORT));
        assertEquals(MatchType.UNSATISFIED,graph.relation(WishCapability.SPACECRAFT,WishCapability.GIVE_ITEM));
    }
}
