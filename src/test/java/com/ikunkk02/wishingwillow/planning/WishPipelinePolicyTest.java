package com.ikunkk02.wishingwillow.planning;

import com.ikunkk02.wishingwillow.ai.WishCapability;
import com.ikunkk02.wishingwillow.ai.WishDelivery;
import com.ikunkk02.wishingwillow.execution.ExecutionSettingsSnapshot;
import com.ikunkk02.wishingwillow.execution.PredefinedWishEventRegistry;
import com.ikunkk02.wishingwillow.research.FeatureType;
import com.ikunkk02.wishingwillow.research.KnowledgeLevel;
import com.ikunkk02.wishingwillow.research.RegistryEntryType;
import com.ikunkk02.wishingwillow.research.VerifiedRegistryResource;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WishPipelinePolicyTest {
    @Test void thirdPartyEntitySeverityIsRejectedDuringPlanningAndAcceptedAtSeventy(){
        var candidate=PlanningFixtures.candidate("candidate-001",WishCapability.STALKING_ENTITY,
                RegistryEntryType.ENTITY,"cavedweller:cave_dweller");
        var low=PlanningFixtures.interpretation(50,WishDelivery.HIDDEN,WishCapability.STALKING_ENTITY);
        String lowJson=PlanningFixtures.planJson(low,"candidate-001",
                "{\"count\":1,\"distance_min\":12,\"distance_max\":24}",
                WishActionType.SPAWN_ENTITY,WishCapability.STALKING_ENTITY);
        assertError(WishPlanError.THIRD_PARTY_ENTITY_SEVERITY,()->WishPlanValidator.parseAndValidate(
                lowJson,low,PlanningFixtures.catalog(candidate),PlanningFixtures.environment(true,true)));
        var wolf=vanillaRegistry("candidate-002",WishCapability.STALKING_ENTITY,RegistryEntryType.ENTITY,
                "minecraft:wolf",FeatureType.ENTITY);
        WishPlanResult repaired=new FallbackWishPlanner().plan("我不想再孤独",low,emptyContext(),
                PlanningFixtures.catalog(candidate,wolf),PlanningFixtures.environment(true,true),
                ExecutionSettingsSnapshot.permissive());
        assertNotNull(repaired.draft());
        assertEquals("minecraft:wolf",repaired.draft().steps().get(0)
                .candidateReference().registryResource().id());

        var allowed=PlanningFixtures.interpretation(70,WishDelivery.HIDDEN,WishCapability.STALKING_ENTITY);
        String allowedJson=PlanningFixtures.planJson(allowed,"candidate-001",
                "{\"count\":1,\"distance_min\":12,\"distance_max\":24}",
                WishActionType.SPAWN_ENTITY,WishCapability.STALKING_ENTITY);
        assertEquals(WishPlanState.READY,WishPlanValidator.parseAndValidate(allowedJson,allowed,
                PlanningFixtures.catalog(candidate),PlanningFixtures.environment(true,true)).state());
    }

    @Test void riskEightyFiveAtSeveritySeventyIsRejectedDuringPlanning(){
        var candidate=vanillaRegistry("candidate-001",WishCapability.POWERFUL_ENEMY,
                RegistryEntryType.ENTITY,"minecraft:wither",FeatureType.ENTITY);
        var interpretation=PlanningFixtures.interpretation(70,WishDelivery.HIDDEN,WishCapability.POWERFUL_ENEMY);
        String json=PlanningFixtures.planJson(interpretation,"candidate-001",
                "{\"count\":1,\"distance_min\":24,\"distance_max\":40}",
                WishActionType.SPAWN_ENTITY,WishCapability.POWERFUL_ENEMY);
        assertError(WishPlanError.RISK_TOO_HIGH,()->WishPlanValidator.parseAndValidate(json,interpretation,
                PlanningFixtures.catalog(candidate),PlanningFixtures.environment(true,true)));
    }

    @Test void builtinRegistryAndPredefinedEventRulesAreAppliedInPlanning(){
        var interpretation=PlanningFixtures.interpretation(40,WishDelivery.HIDDEN,WishCapability.CHANGE_TIME);
        var builtin=builtin("candidate-001",WishCapability.CHANGE_TIME,WishCapability.CHANGE_TIME.name());
        String json=PlanningFixtures.planJson(interpretation,"candidate-001","{\"value\":\"NIGHT\"}",
                WishActionType.CHANGE_TIME,WishCapability.CHANGE_TIME).replace("\"target\":\"PLAYER\"","\"target\":\"WORLD\"");
        assertEquals(WishPlanState.READY,WishPlanValidator.parseAndValidate(json,interpretation,
                PlanningFixtures.catalog(builtin),PlanningFixtures.environment(true,true)).state());

        var untrusted=new CapabilityCandidate("candidate-001",WishCapability.CHANGE_TIME,WishCapability.CHANGE_TIME,
                MatchType.EXACT,CandidateSourceKind.MOD_FEATURE,"cavedweller","Cave Dweller","1.0.0",
                "cavedweller:time",FeatureType.WORLD_SYSTEM,null,"untrusted",KnowledgeLevel.VERIFIED,
                1,1,0,100,25,100);
        assertError(WishPlanError.UNTRUSTED_REGISTRY_CANDIDATE,()->WishPlanValidator.parseAndValidate(json,
                interpretation,PlanningFixtures.catalog(untrusted),PlanningFixtures.environment(true,true)));

        var eventInterpretation=PlanningFixtures.interpretation(70,WishDelivery.HIDDEN,WishCapability.WORLD_EVENT);
        var event=new CapabilityCandidate("candidate-001",WishCapability.WORLD_EVENT,WishCapability.WORLD_EVENT,
                MatchType.EXACT,CandidateSourceKind.MOD_FEATURE,"wishing_willow","Wishing Willow","1.0.0",
                PredefinedWishEventRegistry.OMINOUS_STORM,FeatureType.WORLD_SYSTEM,null,"internal event",
                KnowledgeLevel.VERIFIED,1,1,80,100,55,100);
        String eventJson=PlanningFixtures.planJson(eventInterpretation,"candidate-001","{\"intensity\":2}",
                WishActionType.START_PREDEFINED_EVENT,WishCapability.WORLD_EVENT);
        assertEquals(WishPlanState.READY,WishPlanValidator.parseAndValidate(eventJson,eventInterpretation,
                PlanningFixtures.catalog(event),PlanningFixtures.environment(true,true)).state());
        var foreign=new CapabilityCandidate("candidate-001",WishCapability.WORLD_EVENT,WishCapability.WORLD_EVENT,
                MatchType.EXACT,CandidateSourceKind.MOD_FEATURE,"cavedweller","Cave Dweller","1.0.0",
                PredefinedWishEventRegistry.OMINOUS_STORM,FeatureType.WORLD_SYSTEM,null,"foreign event",
                KnowledgeLevel.VERIFIED,1,1,80,100,55,100);
        assertError(WishPlanError.INVALID_EVENT,()->WishPlanValidator.parseAndValidate(eventJson,eventInterpretation,
                PlanningFixtures.catalog(foreign),PlanningFixtures.environment(true,true)));
    }

    @Test void settingsAndDebugSafeModeFilterBeforeExecutionWhileOrdinaryActionsRemainValid(){
        var block=vanillaRegistry("candidate-001",WishCapability.BLOCK_CHANGE,RegistryEntryType.BLOCK,
                "minecraft:stone",FeatureType.BLOCK);
        var interpretation=PlanningFixtures.interpretation(50,WishDelivery.HIDDEN,WishCapability.BLOCK_CHANGE);
        String json=PlanningFixtures.planJson(interpretation,"candidate-001",
                "{\"distance_min\":2,\"distance_max\":4}",WishActionType.CHANGE_BLOCK,WishCapability.BLOCK_CHANGE);
        var disabled=new ExecutionSettingsSnapshot(true,true,false,true,true,true,false,100,false);
        assertError(WishPlanError.BLOCK_MODIFICATION_DISABLED,()->WishPlanValidator.parseAndValidate(json,
                interpretation,PlanningFixtures.catalog(block),PlanningFixtures.environment(true,true),disabled));
        var debug=new ExecutionSettingsSnapshot(true,true,true,true,true,true,true,100,false);
        assertError(WishPlanError.DEBUG_SAFE_MODE,()->WishPlanValidator.parseAndValidate(json,
                interpretation,PlanningFixtures.catalog(block),PlanningFixtures.environment(true,true),debug));

        var item=vanillaRegistry("candidate-001",WishCapability.GIVE_ITEM,RegistryEntryType.ITEM,
                "minecraft:diamond",FeatureType.ITEM);
        var itemWish=PlanningFixtures.interpretation(30,WishDelivery.HIDDEN,WishCapability.GIVE_ITEM);
        String itemJson=PlanningFixtures.planJson(itemWish,"candidate-001","{\"count\":10}",
                WishActionType.GIVE_ITEM,WishCapability.GIVE_ITEM);
        assertEquals(WishPlanState.READY,WishPlanValidator.parseAndValidate(itemJson,itemWish,
                PlanningFixtures.catalog(item),PlanningFixtures.environment(true,true),debug).state());
        var executionOff=new ExecutionSettingsSnapshot(false,true,true,true,true,true,false,100,false);
        assertError(WishPlanError.EXECUTION_DISABLED,()->WishPlanValidator.parseAndValidate(itemJson,itemWish,
                PlanningFixtures.catalog(item),PlanningFixtures.environment(true,true),executionOff));
    }

    @Test void partialRequiresPrimaryCoverageAndVanillaFallbackPreservesQuantityAndTime(){
        var diamond=vanillaRegistry("candidate-001",WishCapability.GIVE_ITEM,RegistryEntryType.ITEM,
                "minecraft:diamond",FeatureType.ITEM);
        var partialInterpretation=PlanningFixtures.interpretation(30,WishDelivery.HIDDEN,
                WishCapability.GIVE_ITEM,WishCapability.SOUND_EVENT);
        String partialJson=PlanningFixtures.planJson(partialInterpretation,"candidate-001","{\"count\":10}",
                WishActionType.GIVE_ITEM,WishCapability.GIVE_ITEM);
        var validation=WishPlanValidator.parseAndValidate(partialJson,partialInterpretation,
                PlanningFixtures.catalog(diamond),PlanningFixtures.environment(true,true));
        assertEquals(WishPlanState.PARTIAL,validation.state());
        assertFalse(validation.unfulfilledCapabilities().contains(WishCapability.GIVE_ITEM));

        FallbackWishPlanner fallback=new FallbackWishPlanner();
        var diamondInterpretation=PlanningFixtures.interpretation(30,WishDelivery.HIDDEN,WishCapability.GIVE_ITEM);
        WishPlanResult diamondPlan=fallback.plan("我希望获得10颗钻石",diamondInterpretation,
                emptyContext(),PlanningFixtures.catalog(diamond),PlanningFixtures.environment(true,true),
                new ExecutionSettingsSnapshot(true,true,true,true,true,true,true,100,false));
        assertNotNull(diamondPlan.draft());
        assertEquals(10,diamondPlan.draft().steps().stream()
                .mapToInt(step->step.parameters().get("count").getAsInt()).sum());

        var time=builtin("candidate-001",WishCapability.CHANGE_TIME,WishCapability.CHANGE_TIME.name());
        var timeInterpretation=PlanningFixtures.interpretation(30,WishDelivery.HIDDEN,WishCapability.CHANGE_TIME);
        WishPlanResult timePlan=fallback.plan("我希望现在变成晚上",timeInterpretation,emptyContext(),
                PlanningFixtures.catalog(time),PlanningFixtures.environment(true,true),
                ExecutionSettingsSnapshot.permissive());
        assertNotNull(timePlan.draft());
        assertEquals("NIGHT",timePlan.draft().steps().get(0).parameters().get("value").getAsString());
    }

    private static WishContextSnapshot emptyContext(){
        return new WishContextSnapshot("minecraft:overworld",0,"DAY","CLEAR",20,20,20,0,
                "SURVIVAL","minecraft:plains",64,"SURFACE","minecraft:air",List.of(),List.of(),0,0);
    }

    private static CapabilityCandidate vanillaRegistry(String id,WishCapability capability,
                                                        RegistryEntryType type,String resource,FeatureType feature){
        return new CapabilityCandidate(id,capability,capability,MatchType.EXACT,
                CandidateSourceKind.VANILLA_REGISTRY,"minecraft","Minecraft","1.20.1",resource,
                feature,new VerifiedRegistryResource(type,resource),"vanilla",KnowledgeLevel.VERIFIED,
                1,1,0,100,CapabilityMatcher.risk(capability),100);
    }

    private static CapabilityCandidate builtin(String id,WishCapability capability,String featureName){
        return new CapabilityCandidate(id,capability,capability,MatchType.EXACT,
                CandidateSourceKind.VANILLA_BUILTIN,"minecraft","Minecraft","1.20.1",featureName,
                FeatureType.WORLD_SYSTEM,null,"builtin",KnowledgeLevel.VERIFIED,1,1,0,100,
                CapabilityMatcher.risk(capability),100);
    }

    private static void assertError(WishPlanError error,org.junit.jupiter.api.function.Executable executable){
        IllegalArgumentException exception=assertThrows(IllegalArgumentException.class,executable);
        assertEquals(error.name(),exception.getMessage());
    }
}
