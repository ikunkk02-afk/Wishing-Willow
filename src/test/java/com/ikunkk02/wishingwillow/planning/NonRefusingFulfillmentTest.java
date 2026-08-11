package com.ikunkk02.wishingwillow.planning;

import com.ikunkk02.wishingwillow.ai.FulfillmentStyle;
import com.ikunkk02.wishingwillow.ai.WishCapability;
import com.ikunkk02.wishingwillow.ai.WishDelivery;
import com.ikunkk02.wishingwillow.ai.WishFulfillment;
import com.ikunkk02.wishingwillow.ai.WishFulfillmentMode;
import com.ikunkk02.wishingwillow.ai.WishInterpretation;
import com.ikunkk02.wishingwillow.ai.WishTone;
import com.ikunkk02.wishingwillow.contract.WishConstraintKind;
import com.ikunkk02.wishingwillow.contract.WishConstraintOperator;
import com.ikunkk02.wishingwillow.contract.WishContract;
import com.ikunkk02.wishingwillow.contract.WishContractType;
import com.ikunkk02.wishingwillow.contract.WishHardConstraint;
import com.ikunkk02.wishingwillow.contract.WishContractHasher;
import com.ikunkk02.wishingwillow.contract.WishContractValidationState;
import com.ikunkk02.wishingwillow.contract.WishContractValidator;
import com.ikunkk02.wishingwillow.execution.PredefinedWishEventRegistry;
import com.ikunkk02.wishingwillow.execution.ExecutionSettingsSnapshot;
import com.ikunkk02.wishingwillow.research.FeatureType;
import com.ikunkk02.wishingwillow.research.KnowledgeBaseSnapshot;
import com.ikunkk02.wishingwillow.research.KnowledgeBaseState;
import com.ikunkk02.wishingwillow.research.KnowledgeLevel;
import com.ikunkk02.wishingwillow.research.RegistryEntryType;
import com.ikunkk02.wishingwillow.research.VerifiedRegistryResource;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NonRefusingFulfillmentTest {
    @Test void realAiBlockOnlyInterpretationDerivesGiveItemAndPreservesFrozenContract(){
        WishInterpretation interpretation=diamondBlockContract(62,WishCapability.BLOCK_CHANGE);
        String frozenHash=WishContractHasher.contractHash(interpretation);
        var registry=PlanningFixtures.registry(Map.of(
                RegistryEntryType.ITEM,List.of("minecraft:diamond_block"),
                RegistryEntryType.BLOCK,List.of("minecraft:diamond_block")));
        ExecutionSettingsSnapshot safeMode=new ExecutionSettingsSnapshot(true,true,true,
                true,false,false,true,80,false);

        CapabilityCatalog catalog=new CapabilityMatcher().match("我想要100块钻石块",interpretation,
                new KnowledgeBaseSnapshot(KnowledgeBaseState.RUNNING,false,List.of()),registry,safeMode);

        assertEquals(frozenHash,WishContractHasher.contractHash(interpretation));
        assertEquals(MatchType.UNSATISFIED,catalog.matchSets().stream()
                .filter(set->set.capability()==WishCapability.BLOCK_CHANGE).findFirst().orElseThrow().quality());
        assertEquals(MatchType.EXACT,catalog.matchSets().stream()
                .filter(set->set.capability()==WishCapability.GIVE_ITEM).findFirst().orElseThrow().quality());
        assertEquals(List.of(WishCapability.GIVE_ITEM),catalog.candidates().stream()
                .map(CapabilityCandidate::requestedCapability).distinct().toList());

        WishPlanResult result=new FallbackWishPlanner().plan("我想要100块钻石块",interpretation,
                emptyContext(),catalog,new RegistrySnapshotEnvironment(registry),safeMode);
        assertNotNull(result.draft());
        assertEquals(List.of(64,36),result.draft().steps().stream()
                .map(step->step.parameters().get("count").getAsInt()).toList());
        assertEquals(List.of(WishActionType.GIVE_ITEM,WishActionType.GIVE_ITEM),
                result.draft().steps().stream().map(WishPlanStep::action).toList());
    }

    @Test void refusalProseIsRejectedBeforeItCanBecomePlayerOutput(){
        WishInterpretation interpretation=PlanningFixtures.interpretation(30,WishDelivery.IMMEDIATE,
                WishCapability.GIVE_ITEM);
        CapabilityCandidate diamond=vanilla("candidate-001",WishCapability.GIVE_ITEM,
                RegistryEntryType.ITEM,"minecraft:diamond");
        String plan=PlanningFixtures.planJson(interpretation,"candidate-001","{\"count\":1}",
                WishActionType.GIVE_ITEM,WishCapability.GIVE_ITEM)
                .replace("A verified plan","I cannot safely fulfill this wish");
        IllegalArgumentException error=assertThrows(IllegalArgumentException.class,()->
                WishPlanValidator.parseAndValidate(plan,interpretation,PlanningFixtures.catalog(diamond),
                        PlanningFixtures.environment(true,true)));
        assertEquals(WishPlanError.REFUSAL_RESPONSE.name(),error.getMessage());
    }

    @Test void oneHundredDiamondBlocksBecomeSixtyFourPlusThirtySixWithoutBlockChanges(){
        WishInterpretation interpretation=diamondBlockContract(
                WishCapability.BLOCK_CHANGE,WishCapability.GIVE_ITEM);
        CapabilityCandidate block=vanilla("candidate-001",WishCapability.BLOCK_CHANGE,
                RegistryEntryType.BLOCK,"minecraft:diamond_block");
        CapabilityCandidate item=vanilla("candidate-002",WishCapability.GIVE_ITEM,
                RegistryEntryType.ITEM,"minecraft:diamond_block");
        ExecutionSettingsSnapshot safeMode=new ExecutionSettingsSnapshot(true,true,false,
                false,false,false,true,100,false);

        WishPlanResult result=new FallbackWishPlanner().plan("我想要100块钻石块",interpretation,
                emptyContext(),PlanningFixtures.catalog(block,item),PlanningFixtures.environment(true,true),safeMode);

        assertNotNull(result.draft());
        assertEquals(WishPlanState.VALIDATING,result.state());
        assertEquals(List.of(WishActionType.GIVE_ITEM,WishActionType.GIVE_ITEM),
                result.draft().steps().stream().map(WishPlanStep::action).toList());
        assertEquals(List.of(64,36),result.draft().steps().stream()
                .map(step->step.parameters().get("count").getAsInt()).toList());
        assertEquals(100,result.draft().steps().stream()
                .mapToInt(step->step.parameters().get("count").getAsInt()).sum());
    }

    @Test void schemaTwoReadinessDependsOnContractInsteadOfDiscardedMethodCapabilities(){
        WishInterpretation interpretation=diamondBlockContract(
                WishCapability.BLOCK_CHANGE,WishCapability.GIVE_ITEM);
        CapabilityCandidate item=vanilla("candidate-001",WishCapability.GIVE_ITEM,
                RegistryEntryType.ITEM,"minecraft:diamond_block");
        String plan="""
                {"schema_version":1,"summary":"The exact resource is granted","delivery":"IMMEDIATE",
                 "severity":30,"estimated_duration":"INSTANT","steps":[
                  {"step_index":0,"timing":"IMMEDIATE","delay_seconds":0,"trigger":"NONE",
                   "action":"GIVE_ITEM","capability":"GIVE_ITEM","candidate_id":"candidate-001",
                   "target":"PLAYER","parameters":{"count":64},"selection_reason":"First stack"},
                  {"step_index":1,"timing":"IMMEDIATE","delay_seconds":0,"trigger":"NONE",
                   "action":"GIVE_ITEM","capability":"GIVE_ITEM","candidate_id":"candidate-001",
                   "target":"PLAYER","parameters":{"count":36},"selection_reason":"Remaining blocks"}]}
                """;
        WishPlanValidation validation=WishPlanValidator.parseAndValidate(plan,interpretation,
                PlanningFixtures.catalog(item),PlanningFixtures.environment(true,true));
        assertEquals(WishPlanState.READY,validation.state());
        assertEquals(List.of(WishCapability.BLOCK_CHANGE),validation.unfulfilledCapabilities().stream().toList());
    }

    @Test void allBuffsSkipSingleEffectsAndDecorationThenUseExactBuiltinTool(){
        WishContract contract=new WishContract(WishContractType.CHANGE_PLAYER_STATE,
                "Every positive status effect is applied to the player",List.of(
                constraint(WishConstraintKind.STATE_METRIC,WishConstraintOperator.EQUALS,
                        "all_positive_status_effects",0,true),
                constraint(WishConstraintKind.STATE_DIRECTION,WishConstraintOperator.INCREASE,
                        "increase",0,true),
                constraint(WishConstraintKind.TARGET_SCOPE,WishConstraintOperator.EQUALS,
                        "player",0,true)));
        WishInterpretation interpretation=new WishInterpretation(2,"all buffs",
                contract.requiredOutcome(),contract,
                new WishFulfillment(WishFulfillmentMode.ABSURD,"Apply every beneficial effect",
                        List.of(FulfillmentStyle.IRONIC),90),"Exact player state",
                WishTone.ABSURD,40,WishDelivery.IMMEDIATE,
                List.of(WishCapability.POWER_BUFF,WishCapability.SOUND_EVENT,WishCapability.VISUAL_EVENT));
        var registry=PlanningFixtures.registry(Map.of(
                RegistryEntryType.EFFECT,List.of("minecraft:strength"),
                RegistryEntryType.SOUND,List.of("minecraft:ambient.cave"),
                RegistryEntryType.PARTICLE,List.of("minecraft:smoke")));
        CapabilityCatalog catalog=new CapabilityMatcher().match("我要全部buff",interpretation,
                new KnowledgeBaseSnapshot(KnowledgeBaseState.RUNNING,false,List.of()),registry);

        WishPlanResult result=new FallbackWishPlanner().plan("我要全部buff",interpretation,
                emptyContext(),catalog,new RegistrySnapshotEnvironment(registry),
                ExecutionSettingsSnapshot.permissive());

        assertNotNull(result.draft());
        assertEquals(1,result.draft().steps().size());
        WishPlanStep step=result.draft().steps().get(0);
        assertEquals(WishActionType.START_PREDEFINED_EVENT,step.action());
        assertEquals(PredefinedWishEventRegistry.ALL_POSITIVE_EFFECTS,
                step.candidateReference().featureName());
        assertEquals(WishContractValidationState.CONTRACT_FULFILLED,
                WishContractValidator.validate(interpretation,result.draft()).state());
    }

    private static WishInterpretation diamondBlockContract(WishCapability... capabilities){
        return diamondBlockContract(30,capabilities);
    }

    private static WishInterpretation diamondBlockContract(int severity,WishCapability... capabilities){
        WishContract contract=new WishContract(WishContractType.OBTAIN_RESOURCE,
                "The player owns 100 real, accessible diamond blocks",List.of(
                constraint(WishConstraintKind.RESOURCE_SEMANTIC,WishConstraintOperator.EQUALS,
                        "diamond_block",0,true),
                constraint(WishConstraintKind.MINIMUM_QUANTITY,WishConstraintOperator.AT_LEAST,
                        "",100,true),
                constraint(WishConstraintKind.REAL_RESOURCE,WishConstraintOperator.REQUIRED,"",0,true),
                constraint(WishConstraintKind.PLAYER_ACCESSIBLE,WishConstraintOperator.REQUIRED,"",0,true)));
        return new WishInterpretation(2,"obtain diamond blocks","Own 100 diamond blocks",contract,
                new WishFulfillment(WishFulfillmentMode.ABSURD,"Grant the real blocks in two stacks",
                        List.of(FulfillmentStyle.IRONIC),88),"Exact quantity must remain frozen",
                WishTone.ABSURD,severity,WishDelivery.IMMEDIATE,
                List.of(capabilities));
    }

    private static WishHardConstraint constraint(WishConstraintKind kind,WishConstraintOperator operator,
                                                 String semantic,int quantity,boolean required){
        return new WishHardConstraint(kind,operator,semantic,quantity,0,required);
    }

    private static CapabilityCandidate vanilla(String id,WishCapability capability,
                                                RegistryEntryType type,String resource){
        return new CapabilityCandidate(id,capability,capability,MatchType.EXACT,
                CandidateSourceKind.VANILLA_REGISTRY,"minecraft","Minecraft","1.20.1",resource,
                type==RegistryEntryType.ITEM?FeatureType.ITEM:FeatureType.BLOCK,
                new VerifiedRegistryResource(type,resource),"vanilla registry",KnowledgeLevel.VERIFIED,
                1,1,0,100,CapabilityMatcher.risk(capability),100);
    }

    private static WishContextSnapshot emptyContext(){
        return new WishContextSnapshot("minecraft:overworld",0,"DAY","CLEAR",20,20,20,0,
                "SURVIVAL","minecraft:plains",64,"SURFACE","minecraft:air",List.of(),List.of(),0,0);
    }
}
