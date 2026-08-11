package com.ikunkk02.wishingwillow.omen;

import com.google.gson.JsonObject;
import com.ikunkk02.wishingwillow.ai.WishCapability;
import com.ikunkk02.wishingwillow.ai.WishDelivery;
import com.ikunkk02.wishingwillow.ai.WishInterpretation;
import com.ikunkk02.wishingwillow.ai.WishTone;
import com.ikunkk02.wishingwillow.network.packet.WishOmenPacket;
import com.ikunkk02.wishingwillow.planning.WishActionType;
import com.ikunkk02.wishingwillow.planning.WishEstimatedDuration;
import com.ikunkk02.wishingwillow.planning.WishPlan;
import com.ikunkk02.wishingwillow.planning.WishPlanStep;
import com.ikunkk02.wishingwillow.planning.WishStepTiming;
import com.ikunkk02.wishingwillow.planning.WishTargetType;
import com.ikunkk02.wishingwillow.planning.WishTriggerType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WishOmenGeneratorTest {
    @Test
    void stalkingWinsThePrimaryCapabilityPriority() {
        List<WishPlanStep> steps = List.of(
                step(0, WishActionType.GIVE_ITEM, WishCapability.GIVE_ITEM),
                step(1, WishActionType.FOLLOW_PLAYER, WishCapability.STALKING_ENTITY),
                step(2, WishActionType.MODIFY_HEALTH, WishCapability.DAMAGE)
        );

        assertEquals(WishOmenCategory.STALKING, WishOmenGenerator.selectCategory(
                List.of(WishCapability.GIVE_ITEM, WishCapability.DAMAGE, WishCapability.STALKING_ENTITY), steps
        ));
    }

    @Test
    void hiddenAlwaysOverridesTheCapabilityPool() {
        UUID id = UUID.fromString("b8fca66c-6d8a-4e03-bfc1-78a4a4b76e10");
        WishOmen omen = WishOmenGenerator.generate(id,
                interpretation(WishDelivery.HIDDEN, "SECRET_TWISTED_OUTCOME"),
                plan(id, WishDelivery.HIDDEN, List.of(step(0, WishActionType.FOLLOW_PLAYER,
                        WishCapability.STALKING_ENTITY))));

        assertTrue(omen.translationKey().startsWith("omen.wishing_willow.delivery.hidden."));
        assertTrue(omen.delayTicks() >= 40 && omen.delayTicks() <= 100);
    }

    @Test
    void seedIsStableAndPayloadCannotContainInterpretationProse() {
        UUID id = UUID.fromString("1fd5c81f-8dc7-442f-8c49-93061ef10724");
        WishInterpretation interpretation = interpretation(WishDelivery.IMMEDIATE,
                "A VERY SPECIFIC SECRET CONSEQUENCE");
        WishPlan plan = plan(id, WishDelivery.IMMEDIATE,
                List.of(step(0, WishActionType.GIVE_ITEM, WishCapability.STRONG_WEAPON)));

        WishOmen first = WishOmenGenerator.generate(id, interpretation, plan);
        WishOmen second = WishOmenGenerator.generate(id, interpretation, plan);
        WishOmenPacket packet = new WishOmenPacket(first);

        assertEquals(first, second);
        assertTrue(first.translationKey().startsWith("omen.wishing_willow.capability.gift."));
        assertFalse(first.toString().contains("SPECIFIC SECRET"));
        assertFalse(packet.toString().contains("SPECIFIC SECRET"));
    }

    @Test
    void everyCapabilityPoolHasThreeToFiveVariants() {
        for (WishOmenCategory category : WishOmenCategory.values()) {
            assertTrue(WishOmenGenerator.poolSize(category) >= 3);
            assertTrue(WishOmenGenerator.poolSize(category) <= 5);
        }
    }

    @Test
    void delayedDeliveryStablyUsesBothDeliveryAndCapabilityPools() {
        boolean sawDelivery = false;
        boolean sawCapability = false;
        for (int index = 0; index < 60; index++) {
            UUID id = new UUID(0x5A17L, index);
            WishOmen omen = WishOmenGenerator.generate(id,
                    interpretation(WishDelivery.DELAYED, "SECRET"),
                    plan(id, WishDelivery.DELAYED,
                            List.of(step(0, WishActionType.GIVE_ITEM, WishCapability.GIVE_ITEM))));
            sawDelivery |= omen.translationKey().contains(".delivery.delayed.");
            sawCapability |= omen.translationKey().contains(".capability.gift.");
        }
        assertTrue(sawDelivery);
        assertTrue(sawCapability);
    }

    @Test
    void recentHistorySuppressesDuplicatesAndKeepsOnlyThirtyTwoSessions() {
        WishOmenHistory history = new WishOmenHistory(32);
        UUID duplicate = UUID.randomUUID();
        assertTrue(history.accept(duplicate));
        assertFalse(history.accept(duplicate));
        for (int index = 0; index < 40; index++) history.accept(new UUID(9L, index));
        assertEquals(32, history.size());
        assertTrue(history.accept(duplicate));
    }

    private static WishInterpretation interpretation(WishDelivery delivery, String twisted) {
        return new WishInterpretation(1, "intent", "literal", "SECRET_LOOPHOLE", twisted,
                "SECRET_REASONING", WishTone.HORROR, 60, delivery,
                List.of(WishCapability.STALKING_ENTITY, WishCapability.GIVE_ITEM));
    }

    private static WishPlan plan(UUID sessionId, WishDelivery delivery, List<WishPlanStep> steps) {
        return new WishPlan(UUID.randomUUID(), sessionId, 1, "SECRET_PLAN_SUMMARY", delivery, 60,
                WishEstimatedDuration.INSTANT, steps, Set.of("minecraft"), Set.of(), Set.of(),
                1L, 1L, "VERIFIED", "", "", "");
    }

    private static WishPlanStep step(int index, WishActionType action, WishCapability capability) {
        return new WishPlanStep(index, WishStepTiming.IMMEDIATE, 0, WishTriggerType.NONE,
                action, capability, "candidate", WishTargetType.PLAYER, new JsonObject(),
                "SECRET_SELECTION_REASON", null);
    }
}
