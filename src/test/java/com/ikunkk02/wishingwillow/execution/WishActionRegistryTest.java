package com.ikunkk02.wishingwillow.execution;

import com.google.gson.JsonObject;
import com.ikunkk02.wishingwillow.ai.WishCapability;
import com.ikunkk02.wishingwillow.execution.action.WishActionRegistry;
import com.ikunkk02.wishingwillow.planning.CandidateReference;
import com.ikunkk02.wishingwillow.planning.CandidateSourceKind;
import com.ikunkk02.wishingwillow.planning.MatchType;
import com.ikunkk02.wishingwillow.planning.WishActionType;
import com.ikunkk02.wishingwillow.planning.WishStepTiming;
import com.ikunkk02.wishingwillow.planning.WishTargetType;
import com.ikunkk02.wishingwillow.planning.WishTriggerType;
import com.ikunkk02.wishingwillow.research.FeatureType;
import com.ikunkk02.wishingwillow.research.RegistryEntryType;
import com.ikunkk02.wishingwillow.research.VerifiedRegistryResource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WishActionRegistryTest {
    @Test
    void registersEveryWhitelistedAction() {
        var registry = WishActionRegistry.defaults();
        assertEquals(WishActionType.values().length, registry.registered().size());
        for (WishActionType action : WishActionType.values()) assertNotNull(registry.get(action), action.name());
    }

    @Test
    void behaviorPolicyConsumesTheSameSchemaLimits() {
        var registry = WishActionRegistry.defaults();
        assertBehaviorLimit(registry, "follow_player", WishActionType.FOLLOW_PLAYER, 32);
        assertBehaviorLimit(registry, "avoid_player", WishActionType.AVOID_PLAYER, 32);
        assertBehaviorLimit(registry, "set_entity_target", WishActionType.CHANGE_MOB_TARGET, 32);
    }

    private static void assertBehaviorLimit(WishActionRegistry registry, String id,
                                            WishActionType type, int maximum) {
        JsonObject properties = registry.find(id).parameterSchema().getAsJsonObject("properties");
        assertEquals(maximum, properties.getAsJsonObject("max_entities").get("maximum").getAsInt());
        JsonObject parameters = new JsonObject();
        parameters.addProperty("radius", 16);
        parameters.addProperty("max_entities", maximum);
        if (type == WishActionType.CHANGE_MOB_TARGET) parameters.addProperty("disposition", "PLAYER");
        else parameters.addProperty("duration_seconds", 600);
        CandidateReference candidate = new CandidateReference("behavior", WishCapability.MOB_BEHAVIOR,
                WishCapability.MOB_BEHAVIOR, MatchType.EXACT, CandidateSourceKind.VANILLA_REGISTRY,
                "minecraft", "1.20.1", "minecraft:zombie", FeatureType.ENTITY,
                new VerifiedRegistryResource(RegistryEntryType.ENTITY, "minecraft:zombie"), 100, 20);
        assertTrue(WishActionPolicy.validate(candidate, type, parameters,
                WishTargetType.NEARBY_ENTITIES, WishStepTiming.IMMEDIATE, 0,
                WishTriggerType.NONE, 50).allowed());
        parameters.addProperty("max_entities", maximum + 1);
        assertFalse(WishActionPolicy.validate(candidate, type, parameters,
                WishTargetType.NEARBY_ENTITIES, WishStepTiming.IMMEDIATE, 0,
                WishTriggerType.NONE, 50).allowed());
    }
}
