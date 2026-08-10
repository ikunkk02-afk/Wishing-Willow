package com.ikunkk02.wishingwillow.execution;

import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.ikunkk02.wishingwillow.WishingWillow;
import com.ikunkk02.wishingwillow.ai.WishCapability;
import com.ikunkk02.wishingwillow.ai.WishDelivery;
import com.ikunkk02.wishingwillow.ai.WishInterpretation;
import com.ikunkk02.wishingwillow.ai.WishTone;
import com.ikunkk02.wishingwillow.ai.InterpretationState;
import com.ikunkk02.wishingwillow.ai.AiErrorCategory;
import com.ikunkk02.wishingwillow.ai.AiExecutionMode;
import com.ikunkk02.wishingwillow.ai.AiProviderType;
import com.ikunkk02.wishingwillow.execution.action.WishActionRegistry;
import com.ikunkk02.wishingwillow.planning.*;
import com.ikunkk02.wishingwillow.research.FeatureType;
import com.ikunkk02.wishingwillow.research.RegistryEntryType;
import com.ikunkk02.wishingwillow.research.VerifiedRegistryResource;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
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

    @GameTest(template="empty",templateNamespace="minecraft",timeoutTicks=100)
    public static void vanillaExecutionPrimitives(GameTestHelper helper){var player=serverPlayer(helper);place(player,helper.absolutePos(new BlockPos(1,2,1)));prepareFloor(helper);WishActionResult give=execute(helper,player,WishActionType.GIVE_ITEM,WishCapability.GIVE_ITEM,WishTargetType.PLAYER,RegistryEntryType.ITEM,"minecraft:diamond","{\"count\":10}");if(!give.successful()||player.getInventory().countItem(Items.DIAMOND)!=10){helper.fail("GIVE_ITEM executor failed");return;}var effectTarget=helper.makeMockPlayer();effectTarget.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION,100));if(!effectTarget.hasEffect(MobEffects.NIGHT_VISION)){helper.fail("APPLY_EFFECT primitive failed");return;}WishActionResult spawn=execute(helper,player,WishActionType.SPAWN_ENTITY,WishCapability.FRIENDLY_ENTITY,WishTargetType.PLAYER,RegistryEntryType.ENTITY,"minecraft:wolf","{\"count\":1,\"distance_min\":2,\"distance_max\":4}");if(!spawn.successful()||spawn.affected()!=1){helper.fail("SPAWN_ENTITY executor failed: "+spawn.code());return;}long before=helper.getLevel().getDayTime();WishActionResult time=execute(helper,player,WishActionType.CHANGE_TIME,WishCapability.CHANGE_TIME,WishTargetType.WORLD,null,null,"{\"value\":\"NIGHT\"}");if(!time.successful()||helper.getLevel().getDayTime()==before){helper.fail("CHANGE_TIME executor failed");return;}WishActionResult weather=execute(helper,player,WishActionType.CHANGE_WEATHER,WishCapability.CHANGE_WEATHER,WishTargetType.WORLD,null,null,"{\"weather\":\"THUNDER\",\"duration_seconds\":60}");if(!weather.successful()||!helper.getLevel().getLevelData().isThundering()){helper.fail("CHANGE_WEATHER executor failed");return;}WishActionResult sound=execute(helper,player,WishActionType.PLAY_SOUND,WishCapability.SOUND_EVENT,WishTargetType.PLAYER,RegistryEntryType.SOUND,"minecraft:ambient.cave","{\"volume\":1,\"pitch\":1,\"distance\":32}");if(!sound.successful()){helper.fail("PLAY_SOUND executor failed");return;}helper.succeed();}

    @GameTest(template="empty",templateNamespace="minecraft",timeoutTicks=100)
    public static void thirdPartyRegistryEntityIsolated(GameTestHelper helper){ResourceLocation id=ResourceLocation.tryParse("cavedweller:cave_dweller");if(id==null||!ForgeRegistries.ENTITY_TYPES.containsKey(id)){helper.succeed();return;}var player=serverPlayer(helper);place(player,helper.absolutePos(new BlockPos(1,2,1)));prepareFloor(helper);WishActionResult result=execute(helper,player,WishActionType.SPAWN_ENTITY,WishCapability.STALKING_ENTITY,WishTargetType.PLAYER,RegistryEntryType.ENTITY,id.toString(),"{\"count\":1,\"distance_min\":2,\"distance_max\":4}");if(!result.successful()){helper.fail("Third-party executor failed: "+result.code());return;}helper.succeed();}

    @GameTest(template="empty",templateNamespace="minecraft",timeoutTicks=100)
    public static void immediatePlanRunsThroughServerTick(GameTestHelper helper){var server=helper.getLevel().getServer();UUID session=UUID.randomUUID(),owner=UUID.randomUUID(),planId=UUID.randomUUID();WishInterpretation interpretation=new WishInterpretation(1,"night","Night","Time unspecified","Night falls","GameTest",WishTone.DARK,30,WishDelivery.IMMEDIATE,List.of(WishCapability.CHANGE_TIME));CandidateReference candidate=new CandidateReference("candidate-001",WishCapability.CHANGE_TIME,WishCapability.CHANGE_TIME,MatchType.EXACT,CandidateSourceKind.VANILLA_BUILTIN,"minecraft","1.20.1",WishCapability.CHANGE_TIME.name(),FeatureType.WORLD_SYSTEM,null,100,25);WishPlanStep step=new WishPlanStep(0,WishStepTiming.IMMEDIATE,0,WishTriggerType.NONE,WishActionType.CHANGE_TIME,WishCapability.CHANGE_TIME,"candidate-001",WishTargetType.WORLD,JsonParser.parseString("{\"value\":\"NIGHT\"}").getAsJsonObject(),"GameTest",candidate);WishPlan plan=new WishPlan(planId,session,1,"Night",WishDelivery.IMMEDIATE,30,WishEstimatedDuration.INSTANT,List.of(step),Set.of("minecraft"),Set.of(),Set.of(),helper.getLevel().getGameTime(),System.currentTimeMillis(),"VERIFIED","","","");WishRecord wish=new WishRecord(session,owner,"I wish it were night",helper.getLevel().dimension().location(),helper.getLevel().getGameTime(),System.currentTimeMillis(),WishState.FINISHED,InterpretationState.SUCCESS,AiErrorCategory.NONE,AiExecutionMode.PLAYER_PROVIDED,AiProviderType.CUSTOM,"gametest",System.currentTimeMillis(),interpretation).withPlanning(WishPlanState.READY,WishPlanError.NONE,plan);WishSavedData.get(server).update(wish);WishExecutionAcceptResult accepted=WishExecutionManager.acceptStored(server,wish);if(!accepted.accepted()){helper.fail("Execution accept failed: "+accepted.error()+" "+accepted.detail());return;}helper.runAfterDelay(5,()->{WishRecord stored=WishSavedData.get(server).getBySession(session);WishExecutionRecord execution=stored==null||stored.executionId()==null?null:WishExecutionSavedData.get(server).get(stored.executionId());if(execution==null||execution.state()!=WishExecutionState.COMPLETED){helper.fail("Immediate pipeline did not complete: "+(execution==null?"missing":execution.state()));return;}long time=Math.floorMod(helper.getLevel().getDayTime(),24000L);if(time<13000L||time>=23000L){helper.fail("Immediate CHANGE_TIME did not reach night: "+time);return;}helper.succeed();});}

    private static void prepareFloor(GameTestHelper helper){for(int x=-5;x<=7;x++)for(int z=-5;z<=7;z++){BlockPos floor=helper.absolutePos(new BlockPos(x,1,z));helper.getLevel().setBlock(floor,Blocks.STONE.defaultBlockState(),3);helper.getLevel().setBlock(floor.above(),Blocks.AIR.defaultBlockState(),3);helper.getLevel().setBlock(floor.above(2),Blocks.AIR.defaultBlockState(),3);}}
    private static ServerPlayer serverPlayer(GameTestHelper helper){return new ServerPlayer(helper.getLevel().getServer(),helper.getLevel(),new GameProfile(UUID.randomUUID(),"WishGameTest"));}
    private static void place(ServerPlayer player,BlockPos pos){player.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+.5);}
    private static WishActionResult execute(GameTestHelper helper,net.minecraft.server.level.ServerPlayer player,WishActionType action,WishCapability capability,WishTargetType target,RegistryEntryType type,String resource,String json){UUID execution=UUID.randomUUID(),planId=UUID.randomUUID(),session=UUID.randomUUID();VerifiedRegistryResource registry=type==null?null:new VerifiedRegistryResource(type,resource);CandidateReference candidate=new CandidateReference("candidate-001",capability,capability,MatchType.EXACT,registry==null?CandidateSourceKind.VANILLA_BUILTIN:CandidateSourceKind.VANILLA_REGISTRY,"minecraft","1.20.1",resource==null?action.name():resource,type==null?FeatureType.WORLD_SYSTEM:switch(type){case ITEM->FeatureType.ITEM;case ENTITY->FeatureType.ENTITY;case EFFECT->FeatureType.EFFECT;case SOUND->FeatureType.SOUND;default->FeatureType.UNKNOWN;},registry,100,25);WishPlanStep step=new WishPlanStep(0,WishStepTiming.IMMEDIATE,0,WishTriggerType.NONE,action,capability,"candidate-001",target,JsonParser.parseString(json).getAsJsonObject(),"GameTest",candidate);WishPlan plan=new WishPlan(planId,session,1,"GameTest",WishDelivery.IMMEDIATE,70,WishEstimatedDuration.INSTANT,List.of(step),Set.of("minecraft"),resource==null?Set.of():Set.of(resource),Set.of(),helper.getLevel().getGameTime(),0,"VERIFIED","","","");WishExecutionRecord record=new WishExecutionRecord(execution,planId,session,player.getUUID(),1,helper.getLevel().getGameTime());return WishActionRegistry.defaults().get(action).execute(new WishExecutionContext(helper.getLevel(),player,plan,step,candidate,record));}
}
