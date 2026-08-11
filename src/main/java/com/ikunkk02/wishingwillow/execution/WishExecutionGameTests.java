package com.ikunkk02.wishingwillow.execution;

import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.ikunkk02.wishingwillow.WishingWillow;
import com.ikunkk02.wishingwillow.ai.WishCapability;
import com.ikunkk02.wishingwillow.ai.WishDelivery;
import com.ikunkk02.wishingwillow.ai.WishInterpretation;
import com.ikunkk02.wishingwillow.ai.WishTone;
import com.ikunkk02.wishingwillow.ai.WishFulfillment;
import com.ikunkk02.wishingwillow.ai.WishFulfillmentMode;
import com.ikunkk02.wishingwillow.ai.FulfillmentStyle;
import com.ikunkk02.wishingwillow.ai.InterpretationState;
import com.ikunkk02.wishingwillow.ai.AiErrorCategory;
import com.ikunkk02.wishingwillow.ai.AiExecutionMode;
import com.ikunkk02.wishingwillow.ai.AiProviderType;
import com.ikunkk02.wishingwillow.execution.action.WishActionRegistry;
import com.ikunkk02.wishingwillow.contract.*;
import com.ikunkk02.wishingwillow.network.packet.SubmitWishPlanPacket;
import com.ikunkk02.wishingwillow.planning.*;
import com.ikunkk02.wishingwillow.research.FeatureType;
import com.ikunkk02.wishingwillow.research.KnowledgeLevel;
import com.ikunkk02.wishingwillow.research.RegistryEntryType;
import com.ikunkk02.wishingwillow.research.VerifiedRegistryResource;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.RegisterGameTestsEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import com.ikunkk02.wishingwillow.wish.WishRecord;
import com.ikunkk02.wishingwillow.wish.WishSavedData;
import com.ikunkk02.wishingwillow.wish.WishState;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@GameTestHolder(WishingWillow.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WishExecutionGameTests {
    private WishExecutionGameTests(){}
    public static void register(RegisterGameTestsEvent event){event.register(WishExecutionGameTests.class);}

    @GameTest(template="empty",templateNamespace="minecraft",timeoutTicks=180)
    public static void vanillaExecutionPrimitives(GameTestHelper helper){var player=serverPlayer(helper);place(player,helper.absolutePos(new BlockPos(1,2,1)));prepareFloor(helper);WishActionResult give=execute(helper,player,WishActionType.GIVE_ITEM,WishCapability.GIVE_ITEM,WishTargetType.PLAYER,RegistryEntryType.ITEM,"minecraft:diamond","{\"count\":10}");if(!give.successful()||player.getInventory().countItem(Items.DIAMOND)!=10){helper.fail("GIVE_ITEM executor failed");return;}var effectTarget=helper.makeMockPlayer();effectTarget.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION,100));if(!effectTarget.hasEffect(MobEffects.NIGHT_VISION)){helper.fail("APPLY_EFFECT primitive failed");return;}WishActionResult spawn=execute(helper,player,WishActionType.SPAWN_ENTITY,WishCapability.FRIENDLY_ENTITY,WishTargetType.PLAYER,RegistryEntryType.ENTITY,"minecraft:wolf","{\"count\":1,\"distance_min\":2,\"distance_max\":4}");if(!spawn.successful()||spawn.affected()!=1){helper.fail("SPAWN_ENTITY executor failed: "+spawn.code());return;}long before=helper.getLevel().getDayTime();WishActionResult time=execute(helper,player,WishActionType.CHANGE_TIME,WishCapability.CHANGE_TIME,WishTargetType.WORLD,null,null,"{\"value\":\"NIGHT\"}");if(!time.successful()||helper.getLevel().getDayTime()==before){helper.fail("CHANGE_TIME executor failed");return;}WishActionResult weather=execute(helper,player,WishActionType.CHANGE_WEATHER,WishCapability.CHANGE_WEATHER,WishTargetType.WORLD,null,null,"{\"weather\":\"THUNDER\",\"duration_seconds\":60}");if(!weather.successful()||!helper.getLevel().getLevelData().isThundering()){helper.fail("CHANGE_WEATHER executor failed");return;}WishActionResult sound=execute(helper,player,WishActionType.PLAY_SOUND,WishCapability.SOUND_EVENT,WishTargetType.PLAYER,RegistryEntryType.SOUND,"minecraft:ambient.cave","{\"volume\":1,\"pitch\":1,\"distance\":32}");if(!sound.successful()){helper.fail("PLAY_SOUND executor failed");return;}helper.succeed();}

    @GameTest(template="empty",templateNamespace="minecraft",timeoutTicks=180)
    public static void thirdPartyRegistryEntityIsolated(GameTestHelper helper){ResourceLocation id=ResourceLocation.tryParse("cavedweller:cave_dweller");if(id==null||!ForgeRegistries.ENTITY_TYPES.containsKey(id)){helper.succeed();return;}var player=serverPlayer(helper);place(player,helper.absolutePos(new BlockPos(1,2,1)));prepareFloor(helper);WishActionResult result=execute(helper,player,WishActionType.SPAWN_ENTITY,WishCapability.STALKING_ENTITY,WishTargetType.PLAYER,RegistryEntryType.ENTITY,id.toString(),"{\"count\":1,\"distance_min\":2,\"distance_max\":4}");if(!result.successful()){helper.fail("Third-party executor failed: "+result.code());return;}helper.succeed();}

    @GameTest(template="empty",templateNamespace="minecraft",timeoutTicks=180)
    public static void immediatePlanRunsThroughServerTick(GameTestHelper helper){var server=helper.getLevel().getServer();UUID session=UUID.randomUUID(),owner=UUID.randomUUID(),planId=UUID.randomUUID();WishInterpretation interpretation=new WishInterpretation(1,"night","Night","Time unspecified","Night falls","GameTest",WishTone.DARK,30,WishDelivery.IMMEDIATE,List.of(WishCapability.CHANGE_TIME));CandidateReference candidate=new CandidateReference("candidate-001",WishCapability.CHANGE_TIME,WishCapability.CHANGE_TIME,MatchType.EXACT,CandidateSourceKind.VANILLA_BUILTIN,"minecraft","1.20.1",WishCapability.CHANGE_TIME.name(),FeatureType.WORLD_SYSTEM,null,100,25);WishPlanStep step=new WishPlanStep(0,WishStepTiming.IMMEDIATE,0,WishTriggerType.NONE,WishActionType.CHANGE_TIME,WishCapability.CHANGE_TIME,"candidate-001",WishTargetType.WORLD,JsonParser.parseString("{\"value\":\"NIGHT\"}").getAsJsonObject(),"GameTest",candidate);WishPlan plan=new WishPlan(planId,session,1,"Night",WishDelivery.IMMEDIATE,30,WishEstimatedDuration.INSTANT,List.of(step),Set.of("minecraft"),Set.of(),Set.of(),helper.getLevel().getGameTime(),System.currentTimeMillis(),"VERIFIED","","","");WishRecord wish=new WishRecord(session,owner,"I wish it were night",helper.getLevel().dimension().location(),helper.getLevel().getGameTime(),System.currentTimeMillis(),WishState.FINISHED,InterpretationState.SUCCESS,AiErrorCategory.NONE,AiExecutionMode.PLAYER_PROVIDED,AiProviderType.CUSTOM,"gametest",System.currentTimeMillis(),interpretation).withPlanning(WishPlanState.READY,WishPlanError.NONE,plan);WishSavedData.get(server).update(wish);WishExecutionAcceptResult accepted=WishExecutionManager.acceptStored(server,wish);if(!accepted.accepted()){helper.fail("Execution accept failed: "+accepted.error()+" "+accepted.detail());return;}helper.runAfterDelay(120,()->{WishRecord stored=WishSavedData.get(server).getBySession(session);WishExecutionRecord execution=stored==null||stored.executionId()==null?null:WishExecutionSavedData.get(server).get(stored.executionId());if(execution==null||execution.state()!=WishExecutionState.COMPLETED){helper.fail("Immediate pipeline did not complete: "+(execution==null?"missing":execution.state()));return;}long time=Math.floorMod(helper.getLevel().getDayTime(),24000L);if(time<13000L||time>=23000L){helper.fail("Immediate CHANGE_TIME did not reach night: "+time);return;}helper.succeed();});}

    @GameTest(template="empty",templateNamespace="minecraft",timeoutTicks=180)
    public static void beneficialEffectCategoryExpandsTheLiveRegistry(GameTestHelper helper){ServerPlayer player=effectPlayer(helper);WishActionResult result=execute(helper,player,WishActionType.APPLY_EFFECT_CATEGORY,WishCapability.POWER_BUFF,WishTargetType.PLAYER,null,null,"{\"category\":\"BENEFICIAL\",\"duration_seconds\":600,\"amplifier\":1}");List<net.minecraft.world.effect.MobEffect> persistent=ForgeRegistries.MOB_EFFECTS.getValues().stream().filter(effect->effect.getCategory()==MobEffectCategory.BENEFICIAL&&!effect.isInstantenous()).toList();if(!result.successful()||persistent.isEmpty()){helper.fail("APPLY_EFFECT_CATEGORY executor failed: "+result.code());return;}List<ResourceLocation> missing=persistent.stream().filter(effect->!player.hasEffect(effect)).map(ForgeRegistries.MOB_EFFECTS::getKey).filter(java.util.Objects::nonNull).toList();if(!missing.isEmpty()){helper.fail("Beneficial Registry effects were not applied: "+missing);return;}helper.succeed();}

    @GameTest(template="empty",templateNamespace="minecraft",timeoutTicks=180)
    public static void unknownItemIdIsRejectedByServerValidation(GameTestHelper helper){
        var server=helper.getLevel().getServer();UUID session=UUID.randomUUID(),owner=UUID.randomUUID(),planId=UUID.randomUUID();
        WishInterpretation interpretation=new WishInterpretation(1,"item","Unknown item","","Give item","GameTest",WishTone.ABSURD,70,WishDelivery.IMMEDIATE,List.of(WishCapability.GIVE_ITEM));
        VerifiedRegistryResource resource=new VerifiedRegistryResource(RegistryEntryType.ITEM,"evil:not_registered");
        CandidateReference candidate=new CandidateReference("candidate-001",WishCapability.GIVE_ITEM,WishCapability.GIVE_ITEM,MatchType.EXACT,CandidateSourceKind.MOD_FEATURE,"evil","","evil:not_registered",FeatureType.ITEM,resource,100,20);
        WishPlanStep step=new WishPlanStep(0,WishStepTiming.IMMEDIATE,0,WishTriggerType.NONE,WishActionType.GIVE_ITEM,WishCapability.GIVE_ITEM,"candidate-001",WishTargetType.PLAYER,JsonParser.parseString("{\"count\":1}").getAsJsonObject(),"GameTest",candidate);
        WishPlan plan=new WishPlan(planId,session,1,"Unknown item",WishDelivery.IMMEDIATE,70,WishEstimatedDuration.INSTANT,List.of(step),Set.of("evil"),Set.of("evil:not_registered"),Set.of(),helper.getLevel().getGameTime(),System.currentTimeMillis(),"VERIFIED","","","");
        WishRecord wish=new WishRecord(session,owner,"give unknown item",helper.getLevel().dimension().location(),helper.getLevel().getGameTime(),System.currentTimeMillis(),WishState.FINISHED,InterpretationState.SUCCESS,AiErrorCategory.NONE,AiExecutionMode.PLAYER_PROVIDED,AiProviderType.CUSTOM,"gametest",System.currentTimeMillis(),interpretation).withPlanning(WishPlanState.READY,WishPlanError.NONE,plan);
        WishSavedData.get(server).update(wish);WishExecutionAcceptResult accepted=WishExecutionManager.acceptStored(server,wish);
        if(accepted.accepted()||accepted.error()!=WishExecutionAcceptError.STALE_RESOURCE){helper.fail("Unknown item was not rejected by server validation: "+accepted);return;}helper.succeed();
    }

    @GameTest(template="empty",templateNamespace="minecraft",timeoutTicks=180)
    public static void exactHundredDiamondBlocksSpatialFulfillment(GameTestHelper helper){var player=serverPlayer(helper);place(player,helper.absolutePos(new BlockPos(1,4,1)));prepareFloor(helper);WishActionResult result=execute(helper,player,WishActionType.PLACE_BLOCK_PATTERN,WishCapability.BLOCK_CHANGE,WishTargetType.PLAYER,RegistryEntryType.BLOCK,"minecraft:diamond_block","{\"pattern\":\"ENCLOSURE\",\"count\":100}");long count=BlockPos.betweenClosedStream(player.blockPosition().offset(-4,-4,-4),player.blockPosition().offset(4,6,4)).filter(pos->helper.getLevel().getBlockState(pos).is(Blocks.DIAMOND_BLOCK)).count();if(!result.successful()||result.affected()!=100||count!=100){helper.fail("Expected exactly 100 diamond blocks, result="+result+" count="+count);return;}helper.succeed();}

    @GameTest(template="empty",templateNamespace="minecraft",timeoutTicks=180)
    public static void exactHundredGoldBlocksSpatialFulfillment(GameTestHelper helper){var player=serverPlayer(helper);place(player,helper.absolutePos(new BlockPos(1,4,1)));prepareFloor(helper);WishActionResult result=execute(helper,player,WishActionType.PLACE_BLOCK_PATTERN,WishCapability.BLOCK_CHANGE,WishTargetType.PLAYER,RegistryEntryType.BLOCK,"minecraft:gold_block","{\"pattern\":\"ROOM\",\"count\":100}");long count=BlockPos.betweenClosedStream(player.blockPosition().offset(-4,-4,-4),player.blockPosition().offset(4,6,4)).filter(pos->helper.getLevel().getBlockState(pos).is(Blocks.GOLD_BLOCK)).count();if(!result.successful()||result.affected()!=100||count!=100){helper.fail("Expected exactly 100 gold blocks, result="+result+" count="+count);return;}helper.succeed();}

    @GameTest(template="empty",templateNamespace="minecraft",timeoutTicks=300)
    public static void hundredDiamondBlocksPhysicallyFallAndReachPlayer(GameTestHelper helper){
        ServerPlayer player=serverPlayer(helper);place(player,helper.absolutePos(new BlockPos(1,4,1)));prepareFloor(helper);
        UUID execution=UUID.randomUUID(),planId=UUID.randomUUID(),session=UUID.randomUUID();
        VerifiedRegistryResource resource=new VerifiedRegistryResource(RegistryEntryType.BLOCK,"minecraft:diamond_block");
        CandidateReference candidate=new CandidateReference("candidate-001",WishCapability.GIVE_ITEM,WishCapability.GIVE_ITEM,
                MatchType.EXACT,CandidateSourceKind.VANILLA_REGISTRY,"minecraft","1.20.1","minecraft:diamond_block",
                FeatureType.BLOCK,resource,100,20);
        WishPlanStep step=new WishPlanStep(0,WishStepTiming.IMMEDIATE,0,WishTriggerType.NONE,
                WishActionType.FALLING_BLOCK_SHOWER,WishCapability.GIVE_ITEM,"candidate-001",WishTargetType.PLAYER,
                JsonParser.parseString("{\"count\":100,\"spawn_height\":12,\"radius\":3,\"interval_ticks\":1,\"landing_mode\":\"DELIVER_TO_PLAYER\",\"spread\":\"RANDOM\"}").getAsJsonObject(),
                "GameTest physical delivery",candidate);
        WishPlan plan=new WishPlan(planId,session,1,"Diamond block rain",WishDelivery.IMMEDIATE,60,
                WishEstimatedDuration.SHORT,List.of(step),Set.of("minecraft"),Set.of("minecraft:diamond_block"),Set.of(),
                helper.getLevel().getGameTime(),0,"VERIFIED","","","");
        WishExecutionRecord record=new WishExecutionRecord(execution,planId,session,player.getUUID(),1,helper.getLevel().getGameTime());
        WishExecutionContext context=new WishExecutionContext(helper.getLevel(),player,plan,step,candidate,record);
        var executor=WishActionRegistry.defaults().get(WishActionType.FALLING_BLOCK_SHOWER);
        boolean[] finished={false},sawPhysicalEntity={false};
        helper.onEachTick(()->{
            if(finished[0])return;
            AABB area=new AABB(player.blockPosition()).inflate(16,32,16);
            if(!helper.getLevel().getEntitiesOfClass(FallingBlockEntity.class,area).isEmpty())sawPhysicalEntity[0]=true;
            WishActionResult result=executor.execute(context);
            if(result.status()==WishActionResult.Status.RETRY)return;
            finished[0]=true;
            int delivered=player.getInventory().countItem(Blocks.DIAMOND_BLOCK.asItem());
            if(!result.successful()||result.affected()!=100||delivered!=100||!sawPhysicalEntity[0]){
                helper.fail("Falling delivery failed result="+result+" inventory="+delivered+" sawEntity="+sawPhysicalEntity[0]);return;
            }
            helper.succeed();
        });
    }

    @GameTest(template="empty",templateNamespace="minecraft",timeoutTicks=180)
    public static void fallingSemanticSubmissionStoresAndAccepts(GameTestHelper helper){
        var server=helper.getLevel().getServer();UUID session=UUID.randomUUID(),owner=UUID.randomUUID();
        WishContract contract=new WishContract(WishContractType.OBTAIN_RESOURCE,
                "The player obtains 100 real diamond blocks that fell from the sky",List.of(
                new WishHardConstraint(WishConstraintKind.RESOURCE_SEMANTIC,WishConstraintOperator.EQUALS,"diamond_block",0,0,true),
                new WishHardConstraint(WishConstraintKind.MINIMUM_QUANTITY,WishConstraintOperator.AT_LEAST,"",100,0,true),
                new WishHardConstraint(WishConstraintKind.REAL_RESOURCE,WishConstraintOperator.REQUIRED,"",0,0,true),
                new WishHardConstraint(WishConstraintKind.PLAYER_ACCESSIBLE,WishConstraintOperator.REQUIRED,"",0,0,true),
                new WishHardConstraint(WishConstraintKind.DELIVERY_SEMANTIC,WishConstraintOperator.EQUALS,"fall_from_sky",0,0,true)));
        WishInterpretation interpretation=new WishInterpretation(2,"diamond_block_rain",contract.requiredOutcome(),contract,
                new WishFulfillment(WishFulfillmentMode.ABSURD,"Physical diamond block rain",
                        List.of(FulfillmentStyle.PHYSICAL_ABSURDITY),90),"Vanilla FallingBlockEntity composition",
                WishTone.ABSURD,60,WishDelivery.IMMEDIATE,List.of(WishCapability.GIVE_ITEM));
        VerifiedRegistryResource resource=new VerifiedRegistryResource(RegistryEntryType.BLOCK,"minecraft:diamond_block");
        CapabilityCandidate candidate=new CapabilityCandidate("candidate-001",WishCapability.GIVE_ITEM,WishCapability.GIVE_ITEM,
                MatchType.EXACT,CandidateSourceKind.VANILLA_REGISTRY,"minecraft","Minecraft","1.20.1",
                "minecraft:diamond_block",FeatureType.BLOCK,resource,"falling block recipe",KnowledgeLevel.VERIFIED,
                1,1,0,100,20,100);
        WishPlanStep step=new WishPlanStep(0,WishStepTiming.IMMEDIATE,0,WishTriggerType.NONE,
                WishActionType.FALLING_BLOCK_SHOWER,WishCapability.GIVE_ITEM,candidate.candidateId(),WishTargetType.PLAYER,
                JsonParser.parseString("{\"count\":100,\"spawn_height\":28,\"radius\":10,\"interval_ticks\":2,\"landing_mode\":\"DELIVER_TO_PLAYER\",\"spread\":\"RANDOM\"}").getAsJsonObject(),
                "GameTest semantic recipe",candidate.reference());
        WishPlanDraft draft=new WishPlanDraft(2,"Diamond block rain",WishDelivery.IMMEDIATE,60,
                WishEstimatedDuration.SHORT,List.of(step));
        CapabilityCatalog catalog=CapabilityCatalog.create(List.of(new CapabilityMatchSet(WishCapability.GIVE_ITEM,
                MatchType.EXACT,List.of(candidate))),List.of(candidate),"VERIFIED","", "runtime");
        WishRecord wish=new WishRecord(session,owner,"让100个钻石块从天而降",helper.getLevel().dimension().location(),
                helper.getLevel().getGameTime(),System.currentTimeMillis(),WishState.FINISHED,InterpretationState.SUCCESS,
                AiErrorCategory.NONE,AiExecutionMode.PLAYER_PROVIDED,AiProviderType.CUSTOM,"gametest",
                System.currentTimeMillis(),interpretation);
        WishSavedData.get(server).update(wish);
        SubmitWishPlanPacket packet=SubmitWishPlanPacket.fromResult(session,UUID.randomUUID(),WishPlanResult.success(draft),catalog);
        WishPlanState stored=WishPlanStore.accept(server,session,UUID.randomUUID(),interpretation,packet.draftJson(),packet.catalog());
        WishRecord acceptedWish=WishSavedData.get(server).getBySession(session);
        WishExecutionAcceptResult accepted=WishExecutionManager.acceptStored(server,acceptedWish);
        if(stored!=WishPlanState.READY||!accepted.accepted()){
            helper.fail("Semantic submission pipeline failed state="+stored+" accept="+accepted);return;
        }
        WishExecutionManager.cancel(server,accepted.executionId());
        helper.succeed();
    }

    @GameTest(template="empty",templateNamespace="minecraft",timeoutTicks=180)
    public static void speedCompanionReputationAndHouseAreReal(GameTestHelper helper){var player=serverPlayer(helper);place(player,helper.absolutePos(new BlockPos(1,4,1)));prepareFloor(helper);double before=player.getAttributeValue(Attributes.MOVEMENT_SPEED);WishActionResult speed=execute(helper,player,WishActionType.MODIFY_ATTRIBUTE,WishCapability.PLAYER_ATTRIBUTE,WishTargetType.PLAYER,null,null,"{\"attribute\":\"MOVEMENT_SPEED\",\"operation\":\"MULTIPLY\",\"amount\":1,\"duration_seconds\":3600}");if(!speed.successful()||player.getAttributeValue(Attributes.MOVEMENT_SPEED)<=before){helper.fail("Speed contract did not increase movement speed");return;}WishActionResult companion=execute(helper,player,WishActionType.SPAWN_ENTITY,WishCapability.PERSISTENT_FOLLOWER,WishTargetType.PLAYER,RegistryEntryType.ENTITY,"minecraft:wolf","{\"count\":1,\"distance_min\":2,\"distance_max\":4}");if(!companion.successful()||companion.affected()!=1){helper.fail("Persistent companion did not spawn");return;}Villager villager=EntityType.VILLAGER.create(helper.getLevel());if(villager==null){helper.fail("Villager creation failed");return;}villager.moveTo(player.position().add(2,0,0));helper.getLevel().addFreshEntity(villager);WishActionResult relation=execute(helper,player,WishActionType.CHANGE_REPUTATION,WishCapability.REPUTATION,WishTargetType.NEARBY_ENTITIES,null,null,"{\"delta\":100,\"radius\":64}");if(!relation.successful()||villager.getPlayerReputation(player)<=0){helper.fail("Villager relation was not positive");return;}WishActionResult house=execute(helper,player,WishActionType.CREATE_STRUCTURE,WishCapability.STRUCTURE,WishTargetType.PLAYER,null,null,"{\"template\":\"SIMPLE_HOUSE\"}");long planks=BlockPos.betweenClosedStream(player.blockPosition().offset(-4,-2,-4),player.blockPosition().offset(4,5,4)).filter(pos->helper.getLevel().getBlockState(pos).is(Blocks.OAK_PLANKS)).count();if(!house.successful()||planks<100){helper.fail("Simple house did not materially exist: "+planks);return;}helper.succeed();}

    private static void prepareFloor(GameTestHelper helper){WishExecutionConfig.DEBUG_SAFE_MODE.set(false);for(int x=-5;x<=7;x++)for(int z=-5;z<=7;z++){BlockPos floor=helper.absolutePos(new BlockPos(x,1,z));helper.getLevel().setBlock(floor,Blocks.STONE.defaultBlockState(),3);helper.getLevel().setBlock(floor.above(),Blocks.AIR.defaultBlockState(),3);helper.getLevel().setBlock(floor.above(2),Blocks.AIR.defaultBlockState(),3);}}
    private static ServerPlayer serverPlayer(GameTestHelper helper){return new ServerPlayer(helper.getLevel().getServer(),helper.getLevel(),new GameProfile(UUID.randomUUID(),"WishGameTest"));}
    private static ServerPlayer effectPlayer(GameTestHelper helper){return new ServerPlayer(helper.getLevel().getServer(),helper.getLevel(),new GameProfile(UUID.randomUUID(),"WishEffectTest")){
        @Override protected void onEffectAdded(MobEffectInstance effect,Entity source){}
        @Override protected void onEffectUpdated(MobEffectInstance effect,boolean forced,Entity source){}
    };}
    private static void place(ServerPlayer player,BlockPos pos){player.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+.5);}
    private static WishActionResult execute(GameTestHelper helper,net.minecraft.server.level.ServerPlayer player,WishActionType action,WishCapability capability,WishTargetType target,RegistryEntryType type,String resource,String json){UUID execution=UUID.randomUUID(),planId=UUID.randomUUID(),session=UUID.randomUUID();VerifiedRegistryResource registry=type==null?null:new VerifiedRegistryResource(type,resource);CandidateReference candidate=new CandidateReference("candidate-001",capability,capability,MatchType.EXACT,registry==null?CandidateSourceKind.VANILLA_BUILTIN:CandidateSourceKind.VANILLA_REGISTRY,"minecraft","1.20.1",resource==null?action.name():resource,type==null?FeatureType.WORLD_SYSTEM:switch(type){case ITEM->FeatureType.ITEM;case ENTITY->FeatureType.ENTITY;case EFFECT->FeatureType.EFFECT;case SOUND->FeatureType.SOUND;default->FeatureType.UNKNOWN;},registry,100,25);WishPlanStep step=new WishPlanStep(0,WishStepTiming.IMMEDIATE,0,WishTriggerType.NONE,action,capability,"candidate-001",target,JsonParser.parseString(json).getAsJsonObject(),"GameTest",candidate);WishPlan plan=new WishPlan(planId,session,1,"GameTest",WishDelivery.IMMEDIATE,70,WishEstimatedDuration.INSTANT,List.of(step),Set.of("minecraft"),resource==null?Set.of():Set.of(resource),Set.of(),helper.getLevel().getGameTime(),0,"VERIFIED","","","");WishExecutionRecord record=new WishExecutionRecord(execution,planId,session,player.getUUID(),1,helper.getLevel().getGameTime());return WishActionRegistry.defaults().get(action).execute(new WishExecutionContext(helper.getLevel(),player,plan,step,candidate,record));}
}
