package com.ikunkk02.wishingwillow.planning;

import com.ikunkk02.wishingwillow.ai.WishCapability;
import com.ikunkk02.wishingwillow.ai.WishDelivery;
import com.ikunkk02.wishingwillow.research.RegistryEntryType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WishPlanValidatorTest {
    @Test void acceptsVerifiedCandidateAndRejectsFakeCandidateAndRegistry(){
        var interpretation=PlanningFixtures.interpretation(72,WishDelivery.HIDDEN,WishCapability.STALKING_ENTITY);
        var candidate=PlanningFixtures.candidate("candidate-001",WishCapability.STALKING_ENTITY,RegistryEntryType.ENTITY,"cavedweller:cave_dweller");
        var catalog=PlanningFixtures.catalog(candidate);
        String json=PlanningFixtures.planJson(interpretation,"candidate-001","{\"count\":1,\"distance_min\":24,\"distance_max\":40}",WishActionType.SPAWN_ENTITY,WishCapability.STALKING_ENTITY);
        var valid=WishPlanValidator.parseAndValidate(json,interpretation,catalog,PlanningFixtures.environment(true,true));
        assertEquals(WishPlanState.READY,valid.state());
        assertEquals("cavedweller:cave_dweller",valid.draft().steps().get(0).candidateReference().registryResource().id());
        assertError(WishPlanError.INVALID_CANDIDATE,()->WishPlanValidator.parseAndValidate(json.replace("candidate-001","candidate-999"),interpretation,catalog,PlanningFixtures.environment(true,true)));
        assertError(WishPlanError.INVALID_REGISTRY,()->WishPlanValidator.parseAndValidate(json,interpretation,catalog,PlanningFixtures.environment(true,false)));
    }

    @Test void rejectsInvalidParameterMissingModAndExcessiveRisk(){
        var interpretation=PlanningFixtures.interpretation(72,WishDelivery.HIDDEN,WishCapability.STALKING_ENTITY);
        var candidate=PlanningFixtures.candidate("candidate-001",WishCapability.STALKING_ENTITY,RegistryEntryType.ENTITY,"cavedweller:cave_dweller");
        var catalog=PlanningFixtures.catalog(candidate);
        String invalid=PlanningFixtures.planJson(interpretation,"candidate-001","{\"count\":11,\"distance_min\":24,\"distance_max\":40}",WishActionType.SPAWN_ENTITY,WishCapability.STALKING_ENTITY);
        assertError(WishPlanError.INVALID_PARAMETER,()->WishPlanValidator.parseAndValidate(invalid,interpretation,catalog,PlanningFixtures.environment(true,true)));
        String valid=invalid.replace("\"count\":11","\"count\":1");
        assertError(WishPlanError.MISSING_MOD,()->WishPlanValidator.parseAndValidate(valid,interpretation,catalog,PlanningFixtures.environment(false,true)));
    }

    @Test void rejectsExcessiveStepCountAndDeliveryConflict(){
        var interpretation=PlanningFixtures.interpretation(10,WishDelivery.HIDDEN,WishCapability.STALKING_ENTITY);
        var candidate=PlanningFixtures.candidate("candidate-001",WishCapability.STALKING_ENTITY,RegistryEntryType.ENTITY,"cavedweller:cave_dweller");
        String step="{\"step_index\":%d,\"timing\":\"IMMEDIATE\",\"delay_seconds\":0,\"trigger\":\"NONE\",\"action\":\"SPAWN_ENTITY\",\"capability\":\"STALKING_ENTITY\",\"candidate_id\":\"candidate-001\",\"target\":\"PLAYER\",\"parameters\":{\"count\":1,\"distance_min\":24,\"distance_max\":40},\"selection_reason\":\"test\"}";
        String json="{\"schema_version\":1,\"summary\":\"x\",\"delivery\":\"HIDDEN\",\"severity\":10,\"estimated_duration\":\"SHORT\",\"steps\":["+step.formatted(0)+","+step.formatted(1)+","+step.formatted(2)+"]}";
        assertError(WishPlanError.BUDGET_EXCEEDED,()->WishPlanValidator.parseAndValidate(json,interpretation,PlanningFixtures.catalog(candidate),PlanningFixtures.environment(true,true)));
        var immediate=PlanningFixtures.interpretation(30,WishDelivery.IMMEDIATE,WishCapability.STALKING_ENTITY);
        String delayed=PlanningFixtures.planJson(immediate,"candidate-001","{\"count\":1,\"distance_min\":24,\"distance_max\":40}",WishActionType.SPAWN_ENTITY,WishCapability.STALKING_ENTITY)
                .replace("\"timing\":\"IMMEDIATE\"","\"timing\":\"DELAYED\"").replace("\"delay_seconds\":0","\"delay_seconds\":120").replace("\"trigger\":\"NONE\"","\"trigger\":\"AFTER_DELAY\"");
        assertError(WishPlanError.DELIVERY_CONFLICT,()->WishPlanValidator.parseAndValidate(delayed,immediate,PlanningFixtures.catalog(candidate),PlanningFixtures.environment(true,true)));
    }

    @Test void commandOrUnknownFieldsCannotEnterSchema(){
        assertThrows(IllegalArgumentException.class,()->WishActionType.valueOf("RUN_COMMAND"));
        var interpretation=PlanningFixtures.interpretation(72,WishDelivery.HIDDEN,WishCapability.STALKING_ENTITY);
        var candidate=PlanningFixtures.candidate("candidate-001",WishCapability.STALKING_ENTITY,RegistryEntryType.ENTITY,"cavedweller:cave_dweller");
        String json=PlanningFixtures.planJson(interpretation,"candidate-001","{\"count\":1,\"distance_min\":24,\"distance_max\":40,\"command\":\"/op me\"}",WishActionType.SPAWN_ENTITY,WishCapability.STALKING_ENTITY);
        assertError(WishPlanError.INVALID_PARAMETER,()->WishPlanValidator.parseAndValidate(json,interpretation,PlanningFixtures.catalog(candidate),PlanningFixtures.environment(true,true)));
    }

    @Test void vanillaHundredDiamondsCanBeSplitAcrossBoundedSteps(){
        var interpretation=PlanningFixtures.interpretation(30,WishDelivery.HIDDEN,WishCapability.GIVE_ITEM);
        var base=PlanningFixtures.candidate("candidate-001",WishCapability.GIVE_ITEM,RegistryEntryType.ITEM,"minecraft:diamond");
        var candidate=new CapabilityCandidate(base.candidateId(),base.requestedCapability(),base.providedCapability(),base.matchType(),
                CandidateSourceKind.VANILLA_REGISTRY,"minecraft","Minecraft","1.20.1","Diamond",
                com.ikunkk02.wishingwillow.research.FeatureType.ITEM,base.registryResource(),base.description(),base.knowledgeLevel(),
                1,1,0,100,25,98);
        String step="{\"step_index\":%d,\"timing\":\"IMMEDIATE\",\"delay_seconds\":0,\"trigger\":\"NONE\",\"action\":\"GIVE_ITEM\",\"capability\":\"GIVE_ITEM\",\"candidate_id\":\"candidate-001\",\"target\":\"PLAYER\",\"parameters\":{\"count\":%d},\"selection_reason\":\"Verified diamond\"}";
        String json="{\"schema_version\":1,\"summary\":\"One hundred diamonds\",\"delivery\":\"HIDDEN\",\"severity\":30,\"estimated_duration\":\"INSTANT\",\"steps\":["+step.formatted(0,64)+","+step.formatted(1,36)+"]}";
        var validation=WishPlanValidator.parseAndValidate(json,interpretation,PlanningFixtures.catalog(candidate),PlanningFixtures.environment(true,true));
        assertEquals(WishPlanState.READY,validation.state());
        assertEquals(100,validation.draft().steps().stream().mapToInt(s->s.parameters().get("count").getAsInt()).sum());
    }

    private static void assertError(WishPlanError error,org.junit.jupiter.api.function.Executable executable){
        IllegalArgumentException exception=assertThrows(IllegalArgumentException.class,executable);assertEquals(error.name(),exception.getMessage());
    }
}
