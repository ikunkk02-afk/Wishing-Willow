package com.ikunkk02.wishingwillow.planning;

import com.google.gson.JsonParser;
import com.ikunkk02.wishingwillow.ai.*;
import com.ikunkk02.wishingwillow.research.RegistryEntryType;
import com.ikunkk02.wishingwillow.wish.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class WishPlanSerializationTest {
    @Test void planAndFrozenCandidateRoundTripThroughWishSavedData(){
        UUID session=UUID.randomUUID(),player=UUID.randomUUID();
        CandidateReference reference=PlanningFixtures.candidate("candidate-001",WishCapability.STALKING_ENTITY,
                RegistryEntryType.ENTITY,"cavedweller:cave_dweller").reference();
        WishPlanStep step=new WishPlanStep(0,WishStepTiming.DELAYED,120,WishTriggerType.AFTER_DELAY,
                WishActionType.SPAWN_ENTITY,WishCapability.STALKING_ENTITY,"candidate-001",WishTargetType.PLAYER,
                JsonParser.parseString("{\"count\":1,\"distance_min\":24,\"distance_max\":40}").getAsJsonObject(),"Verified",reference);
        WishPlan plan=new WishPlan(UUID.randomUUID(),session,1,"A watcher follows",WishDelivery.DELAYED,72,
                WishEstimatedDuration.LONG,List.of(step),Set.of("cavedweller"),Set.of("cavedweller:cave_dweller"),Set.of(),
                200,300,"READY","knowledge","registry","catalog");
        WishRecord record=record(session,player).withPlanning(WishPlanState.READY,WishPlanError.NONE,plan);
        WishSavedData data=new WishSavedData();data.update(record);
        WishRecord loaded=WishSavedData.load(data.save(new CompoundTag())).getBySession(session);
        assertNotNull(loaded);assertEquals(WishPlanState.READY,loaded.planState());assertEquals(plan.planId(),loaded.plan().planId());
        assertEquals("cavedweller:cave_dweller",loaded.plan().steps().get(0).candidateReference().registryResource().id());
    }

    @Test void legacyRecordDefaultsToNotPlannedAndInterruptedPlanningFails(){
        WishRecord legacy=WishRecord.load(record(UUID.randomUUID(),UUID.randomUUID()).save());
        assertEquals(WishPlanState.NOT_PLANNED,legacy.planState());
        WishRecord interrupted=record(UUID.randomUUID(),UUID.randomUUID()).withPlanning(WishPlanState.PLANNING,WishPlanError.NONE,null);
        WishRecord loaded=WishRecord.load(interrupted.save());
        assertEquals(WishPlanState.FAILED,loaded.planState());assertEquals(WishPlanError.DISCONNECTED,loaded.planError());
    }

    @Test void storedPlanBecomesInvalidWhenRegistryOrModDisappears(){
        CandidateReference reference=PlanningFixtures.candidate("candidate-001",WishCapability.STALKING_ENTITY,
                RegistryEntryType.ENTITY,"fake_mod:fake_monster").reference();
        WishPlanStep step=new WishPlanStep(0,WishStepTiming.IMMEDIATE,0,WishTriggerType.NONE,WishActionType.SPAWN_ENTITY,
                WishCapability.STALKING_ENTITY,"candidate-001",WishTargetType.PLAYER,JsonParser.parseString("{\"count\":1,\"distance_min\":4,\"distance_max\":8}").getAsJsonObject(),"test",reference);
        WishPlan plan=new WishPlan(UUID.randomUUID(),UUID.randomUUID(),1,"test",WishDelivery.HIDDEN,70,WishEstimatedDuration.SHORT,List.of(step),Set.of("cavedweller"),Set.of("fake_mod:fake_monster"),Set.of(),1,1,"READY","k","r","c");
        assertThrows(IllegalArgumentException.class,()->WishPlanValidator.validateStored(plan,PlanningFixtures.environment(true,false)));
        assertThrows(IllegalArgumentException.class,()->WishPlanValidator.validateStored(plan,PlanningFixtures.environment(false,true)));
    }

    @Test void schemaTwoBatchIdRoundTripsWhileLegacyStepDefaultsBlank(){
        CandidateReference reference = PlanningFixtures.candidate("candidate-001", WishCapability.GIVE_ITEM,
                RegistryEntryType.ITEM, "minecraft:diamond").reference();
        WishPlanStep batched = new WishPlanStep(0, WishStepTiming.IMMEDIATE, 0, WishTriggerType.NONE,
                WishActionType.GIVE_ITEM, WishCapability.GIVE_ITEM, "candidate-001", WishTargetType.PLAYER,
                JsonParser.parseString("{\"count\":64}").getAsJsonObject(), "batch", reference, "items-1");
        WishPlan plan = new WishPlan(UUID.randomUUID(), UUID.randomUUID(), 2, "batch", WishDelivery.IMMEDIATE, 30,
                WishEstimatedDuration.INSTANT, List.of(batched), Set.of("minecraft"), Set.of("minecraft:diamond"),
                Set.of(), 1, 1, "READY", "k", "r", "c");
        assertEquals("items-1", WishPlanNbt.load(WishPlanNbt.save(plan)).steps().get(0).batchId());
        WishPlanStep legacy = new WishPlanStep(0, WishStepTiming.IMMEDIATE, 0, WishTriggerType.NONE,
                WishActionType.GIVE_ITEM, WishCapability.GIVE_ITEM, "candidate-001", WishTargetType.PLAYER,
                JsonParser.parseString("{\"count\":1}").getAsJsonObject(), "legacy", reference);
        assertEquals("", legacy.batchId());
    }

    private static WishRecord record(UUID session,UUID player){
        WishInterpretation interpretation=PlanningFixtures.interpretation(72,WishDelivery.HIDDEN,WishCapability.STALKING_ENTITY);
        return new WishRecord(session,player,"wish",new ResourceLocation("minecraft","overworld"),1,2,WishState.FINISHED,
                InterpretationState.SUCCESS,AiErrorCategory.NONE,AiExecutionMode.PLAYER_PROVIDED,AiProviderType.CUSTOM,"test",3,interpretation);
    }
}
