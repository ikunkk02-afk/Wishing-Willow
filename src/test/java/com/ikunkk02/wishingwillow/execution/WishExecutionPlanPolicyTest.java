package com.ikunkk02.wishingwillow.execution;

import com.ikunkk02.wishingwillow.ai.WishCapability;
import com.ikunkk02.wishingwillow.planning.WishPlanState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WishExecutionPlanPolicyTest {
    @Test
    void schemaTwoReadyPlanMayDiscardMethodCapabilityWhenContractAlreadyPassed() {
        assertFalse(WishExecutionPlanPolicy.readyHasBlockingUnfulfilledCapabilities(
                2, WishPlanState.READY, Set.of(WishCapability.BLOCK_CHANGE)));
    }

    @Test
    void legacyReadyPlanStillRequiresEveryCapability() {
        assertTrue(WishExecutionPlanPolicy.readyHasBlockingUnfulfilledCapabilities(
                1, WishPlanState.READY, Set.of(WishCapability.BLOCK_CHANGE)));
    }

    @Test
    void partialPlanStillRequiresItsPrimaryCapability() {
        assertTrue(WishExecutionPlanPolicy.partialMissesPrimaryCapability(
                WishPlanState.PARTIAL,
                List.of(WishCapability.GIVE_ITEM, WishCapability.BLOCK_CHANGE),
                Set.of(WishCapability.GIVE_ITEM)));
    }
}
