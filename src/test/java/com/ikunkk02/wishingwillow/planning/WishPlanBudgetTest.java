package com.ikunkk02.wishingwillow.planning;

import com.google.gson.JsonParser;
import com.ikunkk02.wishingwillow.ai.WishCapability;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WishPlanBudgetTest {
    @Test void usesSeverityStepAndDestructiveBands(){
        assertEquals(2,WishPlanBudget.maxSteps(10)); assertEquals(6,WishPlanBudget.maxSteps(72)); assertEquals(10,WishPlanBudget.maxSteps(100));
        assertEquals(0,WishPlanBudget.maxDestructiveCost(10)); assertEquals(10,WishPlanBudget.maxDestructiveCost(72)); assertEquals(30,WishPlanBudget.maxDestructiveCost(100));
    }

    @Test void computesExplosionAndHostileEntityCost(){
        var entity=PlanningFixtures.candidate("candidate-001",WishCapability.HOSTILE_ENTITY,
                com.ikunkk02.wishingwillow.research.RegistryEntryType.ENTITY,"cavedweller:cave_dweller").reference();
        var spawn=new WishPlanStep(0,WishStepTiming.IMMEDIATE,0,WishTriggerType.NONE,WishActionType.SPAWN_ENTITY,
                WishCapability.HOSTILE_ENTITY,"candidate-001",WishTargetType.PLAYER,
                JsonParser.parseString("{\"count\":3,\"distance_min\":8,\"distance_max\":16}").getAsJsonObject(),"test",entity);
        assertEquals(6,WishPlanBudget.destructiveCost(spawn));
        var explosion=new WishPlanStep(0,WishStepTiming.IMMEDIATE,0,WishTriggerType.NONE,WishActionType.EXPLOSION,
                WishCapability.EXPLOSION,"candidate-002",WishTargetType.AREA,
                JsonParser.parseString("{\"power\":4,\"destroy_blocks\":true}").getAsJsonObject(),"test",entity);
        assertEquals(8,WishPlanBudget.destructiveCost(explosion));
    }
}
