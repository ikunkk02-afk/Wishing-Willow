package com.ikunkk02.wishingwillow.execution;

import com.google.gson.JsonParser;
import com.ikunkk02.wishingwillow.WishingWillow;
import com.ikunkk02.wishingwillow.ai.WishCapability;
import com.ikunkk02.wishingwillow.execution.WishExecutionScheduler.StepKey;
import com.ikunkk02.wishingwillow.execution.action.WishActionExecutor;
import com.ikunkk02.wishingwillow.execution.action.WishActionRegistry;
import com.ikunkk02.wishingwillow.execution.action.WishExecutionContext;
import com.ikunkk02.wishingwillow.planning.*;
import com.ikunkk02.wishingwillow.wish.WishRecord;
import com.ikunkk02.wishingwillow.wish.WishSavedData;
import com.ikunkk02.wishingwillow.wish.WishPipelineAudit;
import com.ikunkk02.wishingwillow.wish.WishLifecycleLog;
import com.ikunkk02.wishingwillow.wish.WishPipelineState;
import com.ikunkk02.wishingwillow.wish.WishSessionTerminationReason;
import com.ikunkk02.wishingwillow.network.ModNetworking;
import com.ikunkk02.wishingwillow.network.packet.WishPipelineStatePacket;
import com.ikunkk02.wishingwillow.contract.WishContractValidator;
import com.ikunkk02.wishingwillow.contract.WishContractValidationState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import com.ikunkk02.wishingwillow.research.FeatureType;
import com.ikunkk02.wishingwillow.research.RegistryEntryType;
import com.ikunkk02.wishingwillow.research.VerifiedRegistryResource;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;

import javax.annotation.Nullable;
import java.util.*;

public final class WishExecutionManager {
    private static final WishActionRegistry ACTIONS=WishActionRegistry.defaults();
    private static final WishExecutionScheduler SCHEDULER=new WishExecutionScheduler();
    private static final Map<UUID,Boolean> SLEEPING=new HashMap<>(),DARK=new HashMap<>(),CAVE=new HashMap<>();
    private static final Map<UUID,YawSample> YAW=new HashMap<>();
    private static Boolean nightPeriod;
    private static final int CINEMATIC_START_DELAY_TICKS=110;
    private static boolean registered;
    private WishExecutionManager(){}

    public static void register(){if(registered)return;registered=true;WishPersistentSocialRules.register();WishAttractionSavedData.register();MinecraftForge.EVENT_BUS.addListener(WishExecutionManager::onTick);MinecraftForge.EVENT_BUS.addListener(WishExecutionManager::onStarted);MinecraftForge.EVENT_BUS.addListener(WishExecutionManager::onStopped);MinecraftForge.EVENT_BUS.addListener(WishExecutionManager::onDimension);MinecraftForge.EVENT_BUS.addListener(WishExecutionManager::onDeath);MinecraftForge.EVENT_BUS.addListener(WishExecutionCommands::register);}

    public static WishExecutionAcceptResult accept(ServerPlayer sender,WishPlan plan){
        if(sender==null)return WishExecutionAcceptResult.rejected(WishExecutionAcceptError.INVALID_OWNER,"missing player");
        WishRecord wish=plan==null?null:WishSavedData.get(sender.server).getBySession(plan.wishSessionId());
        return acceptInternal(sender.server,wish,plan,sender.getUUID());
    }

    /**
     * @deprecated Legacy WishPlan compatibility only. New Wish Programs start through
     * {@link WishActionManager#startProgram(net.minecraft.server.level.ServerPlayer,
     * com.ikunkk02.wishingwillow.wish.WishRecord,
     * com.ikunkk02.wishingwillow.program.ValidatedWishProgram)} and never enter this path.
     */
    @Deprecated
    public static WishExecutionAcceptResult acceptStored(MinecraftServer server,WishRecord wish){
        if(server==null||wish==null)return WishExecutionAcceptResult.rejected(WishExecutionAcceptError.INVALID_SESSION,"missing stored wish");
        return acceptInternal(server,wish,wish.plan(),null);
    }

    static WishExecutionScheduler scheduler() { return SCHEDULER; }

    private static WishExecutionAcceptResult acceptInternal(MinecraftServer server,WishRecord wish,WishPlan plan,
                                                             @Nullable UUID submittingPlayer){
        if(plan==null)return rejected(wish,WishExecutionAcceptError.INVALID_PLAN,"missing plan",server);
        if(wish==null||!wish.sessionId().equals(plan.wishSessionId()))
            return rejected(wish,WishExecutionAcceptError.INVALID_SESSION,"plan session does not match stored wish",server);
        if(submittingPlayer!=null&&!wish.playerId().equals(submittingPlayer))
            return rejected(wish,WishExecutionAcceptError.INVALID_OWNER,"submitting player does not own wish",server);
        if(wish.plan()==null||!wish.plan().planId().equals(plan.planId()))
            return rejected(wish,WishExecutionAcceptError.INVALID_PLAN,"plan id does not match stored plan",server);
        int interpretationSchema=wish.interpretation()==null?1:wish.interpretation().schemaVersion();
        if(WishExecutionPlanPolicy.readyHasBlockingUnfulfilledCapabilities(
                interpretationSchema,wish.planState(),plan.unfulfilledCapabilities()))
            return rejected(wish,WishExecutionAcceptError.INVALID_PLAN,"READY plan still has unfulfilled capabilities",server);
        if(wish.planState()==WishPlanState.PARTIAL&&(wish.interpretation()==null
                ||WishExecutionPlanPolicy.partialMissesPrimaryCapability(wish.planState(),
                wish.interpretation().requiredCapabilities(),plan.unfulfilledCapabilities())))
            return rejected(wish,WishExecutionAcceptError.INVALID_PLAN,"PARTIAL plan does not cover primary capability",server);
        if(wish.interpretation()!=null){
            var contract=WishContractValidator.validate(wish.interpretation(),plan.steps());
            if(contract.state()==WishContractValidationState.CONTRACT_NOT_FULFILLED)
                return rejected(wish,WishExecutionAcceptError.VALIDATION_FAILED,"wish contract: "+contract.code(),server);
        }

        WishExecutionSavedData data=WishExecutionSavedData.get(server);
        WishExecutionRecord byPlan=data.byPlan(plan.planId());
        WishExecutionRecord bySession=data.bySession(plan.wishSessionId());
        if(byPlan!=null||bySession!=null){
            WishExecutionRecord existing=byPlan!=null?byPlan:bySession;
            if(existing.planId().equals(plan.planId())&&existing.wishSessionId().equals(plan.wishSessionId())
                    &&existing.ownerId().equals(wish.playerId())){
                WishSavedData.get(server).update(wish.withExecution(existing.executionId(),existing.state(),
                        WishExecutionAcceptError.NONE,""));
                WishPipelineAudit.success(wish.sessionId(),"EXECUTION_ACCEPT",
                        "error=ALREADY_ACCEPTED execution="+existing.executionId());
                return WishExecutionAcceptResult.alreadyAccepted(existing.executionId());
            }
            return rejected(wish,WishExecutionAcceptError.DUPLICATE_EXECUTION,
                    "session or plan is already indexed by a different execution",server);
        }
        if(wish.executionId()!=null||wish.executionState().terminal())
            return rejected(wish,WishExecutionAcceptError.PLAN_ALREADY_EXECUTED,
                    "stored wish already has terminal or accepted execution state",server);
        if(!WishExecutionConfig.ENABLED.get())
            return rejected(wish,WishExecutionAcceptError.EXECUTION_DISABLED,"server execution is disabled",server);

        WishingWillow.LOGGER.info("Server action validation session={} plan={} actions={}",
                wish.sessionId(),plan.planId(),plan.steps().stream().map(step->step.action().name()).toList());
        WishExecutionValidationResult validation=WishExecutionValidator.validateDetailed(server,plan,ACTIONS);
        if(!validation.valid()){
            String detail="step="+validation.stepIndex()+" action="+validation.action()+" "+validation.detail();
            WishingWillow.LOGGER.warn(
                    "WishingWillow execution validation rejected session={} plan={} step={} action={} reason={} detail={}",
                    wish.sessionId(),plan.planId(),validation.stepIndex(),validation.action(),validation.error(),validation.detail());
            return rejected(wish,validation.error(),detail,server);
        }

        long now=server.overworld().getGameTime();
        WishExecutionRecord record=new WishExecutionRecord(UUID.randomUUID(),plan.planId(),plan.wishSessionId(),wish.playerId(),plan.steps().size(),now);
        plan.steps().forEach(step->{if(step.candidateReference().registryResource()!=null)record.selectResource(step.stepIndex(),step.candidateReference().registryResource().id());});
        if(!data.add(record))return rejected(wish,WishExecutionAcceptError.DUPLICATE_EXECUTION,"execution index rejected duplicate",server);
        WishSavedData.get(server).update(wish.withExecution(record.executionId(),record.state(),WishExecutionAcceptError.NONE,""));
        schedule(server,record,plan,now);
        WishPipelineAudit.success(wish.sessionId(),"EXECUTION_ACCEPT","execution="+record.executionId());
        WishingWillow.LOGGER.info("Wish execution started session={} execution={} actions={}",
                wish.sessionId(),record.executionId(),plan.steps().size());
        WishingWillow.LOGGER.info("Wish execution accepted session={} execution={}",
                wish.sessionId(), record.executionId());
        return WishExecutionAcceptResult.accepted(record.executionId());
    }

    private static WishExecutionAcceptResult rejected(@Nullable WishRecord wish,WishExecutionAcceptError error,
                                                       String detail,@Nullable MinecraftServer server){
        WishingWillow.LOGGER.warn("WishingWillow execution accept rejected session={} plan={} reason={} detail={}",
                wish==null?null:wish.sessionId(),wish==null||wish.plan()==null?null:wish.plan().planId(),error,detail);
        if(wish!=null&&server!=null){
            WishExecutionState state=error==WishExecutionAcceptError.STALE_RESOURCE?WishExecutionState.STALE:WishExecutionState.FAILED;
            WishSavedData.get(server).update(wish.withExecution(null,state,error,detail));
            WishPipelineAudit.failure(wish.sessionId(),"EXECUTION_ACCEPT",error.name(),detail);
        }
        return WishExecutionAcceptResult.rejected(error,detail);
    }

    public static List<String> dryRun(MinecraftServer server,WishPlan plan){List<String> result=new ArrayList<>();if(plan==null)return List.of("INVALID_PLAN");for(int index=0;index<plan.steps().size();index++)try{WishExecutionValidator.validateStep(server,plan,index,ACTIONS);result.add("Step "+index+": READY");}catch(IllegalArgumentException error){result.add("Step "+index+": "+error.getMessage());}if(result.stream().allMatch(line->line.endsWith("READY")))try{WishExecutionValidator.validate(server,plan,ACTIONS);}catch(IllegalArgumentException error){result.add("Plan: "+error.getMessage());}return result;}
    public static boolean cancel(MinecraftServer server,UUID executionId){return stop(server,executionId,WishExecutionState.CANCELLED);}
    public static boolean supersede(MinecraftServer server,UUID executionId){return stop(server,executionId,WishExecutionState.SUPERSEDED);}
    private static boolean stop(MinecraftServer server,UUID executionId,WishExecutionState terminal){WishExecutionRecord record=WishExecutionSavedData.get(server).get(executionId);if(record==null||record.state().terminal())return false;long now=server.overworld().getGameTime();for(WishStepExecution step:record.steps())if(!step.state().terminal()){SCHEDULER.remove(new StepKey(record.executionId(),step.stepIndex()));step.transition(WishStepExecutionState.CANCELLED,now);}record.transition(terminal,now);changed(server,record);return true;}
    public static boolean debugTrigger(MinecraftServer server,UUID executionId,int stepIndex){WishExecutionRecord record=WishExecutionSavedData.get(server).get(executionId);WishStepExecution step=record==null?null:record.step(stepIndex);if(step==null||step.state()!=WishStepExecutionState.WAITING_TRIGGER)return false;SCHEDULER.remove(new StepKey(executionId,stepIndex));triggered(server,new StepKey(executionId,stepIndex),server.overworld().getGameTime());return true;}
    public static WishActionRegistry actions(){return ACTIONS;}

    private static void schedule(MinecraftServer server,WishExecutionRecord record,WishPlan plan,long now){record.transition(WishExecutionState.SCHEDULED,now);if(plan.summary().startsWith("WishProgram:")){String first=plan.steps().stream().map(WishPlanStep::batchId).findFirst().orElse("");scheduleProgramGroup(record,plan,first,now);}else for(WishPlanStep planned:plan.steps())scheduleStep(record,planned,now);changed(server,record);
    }
    private static void scheduleProgramGroup(WishExecutionRecord record,WishPlan plan,String group,long now){for(WishPlanStep planned:plan.steps())if(planned.batchId().equals(group)&&record.step(planned.stepIndex()).state()==WishStepExecutionState.PENDING)scheduleStep(record,planned,now);}
    private static void scheduleStep(WishExecutionRecord record,WishPlanStep planned,long now){WishStepExecution step=record.step(planned.stepIndex());StepKey key=new StepKey(record.executionId(),planned.stepIndex());switch(planned.timing()){case IMMEDIATE->{step.transition(WishStepExecutionState.READY,now);step.schedule(now+CINEMATIC_START_DELAY_TICKS);SCHEDULER.delay(key,now+CINEMATIC_START_DELAY_TICKS);}case DELAYED->{long at=now+planned.delaySeconds()*20L;step.schedule(at);step.transition(WishStepExecutionState.WAITING_DELAY,now);SCHEDULER.delay(key,at);}case TRIGGERED,DELAYED_AFTER_TRIGGER->{step.transition(WishStepExecutionState.WAITING_TRIGGER,now);SCHEDULER.trigger(key,planned.trigger());}}}

    private static void rebuild(MinecraftServer server){SCHEDULER.clear();WishExecutionSavedData data=WishExecutionSavedData.get(server);for(WishExecutionRecord record:data.all()){for(var lease:record.behaviorLeases().values())if(lease.expires()>server.overworld().getGameTime())try{WishEntityBehaviorManager.bind(record.executionId(),lease.entity(),record.ownerId(),WishEntityBehaviorManager.Mode.valueOf(lease.mode()),lease.speed(),lease.minDistance(),lease.expires());}catch(IllegalArgumentException ignored){}if(record.state().terminal())continue;if(record.isProgram()){WishProgramExecutor.rebuild(server,record);continue;}WishRecord wish=WishSavedData.get(server).getBySession(record.wishSessionId());if(wish==null||wish.plan()==null){record.transition(WishExecutionState.STALE,server.overworld().getGameTime());data.changed();continue;}WishExecutionValidationResult validation=WishExecutionValidator.validateDetailed(server,wish.plan(),ACTIONS);if(!validation.valid()){for(WishStepExecution step:record.steps())if(!step.state().terminal())step.transition(WishStepExecutionState.STALE,server.overworld().getGameTime());record.transition(WishExecutionState.STALE,server.overworld().getGameTime());data.changed();String detail="recovery step="+validation.stepIndex()+" action="+validation.action()+" "+validation.detail();WishSavedData.get(server).update(wish.withExecution(record.executionId(),WishExecutionState.STALE,validation.error(),detail));WishingWillow.LOGGER.warn("WishingWillow execution recovery rejected session={} plan={} execution={} step={} action={} reason={} detail={}",wish.sessionId(),wish.plan().planId(),record.executionId(),validation.stepIndex(),validation.action(),validation.error(),validation.detail());continue;}for(WishStepExecution step:record.steps()){if(step.state().terminal())continue;WishPlanStep planned=wish.plan().steps().get(step.stepIndex());StepKey key=new StepKey(record.executionId(),step.stepIndex());if(step.state()==WishStepExecutionState.WAITING_TRIGGER)SCHEDULER.trigger(key,planned.trigger());else SCHEDULER.delay(key,step.executeAtGameTime()<0?server.overworld().getGameTime():step.executeAtGameTime());}}}

    private static void onTick(TickEvent.ServerTickEvent event){if(event.phase!=TickEvent.Phase.END)return;MinecraftServer server=event.getServer();long now=server.overworld().getGameTime();watchdogPrograms(server,now);NeverAloneSavedData.get(server).tick(server,now);boolean boundedWorldActionUsed=false;for(StepKey key:SCHEDULER.due(now,8)){if(isBoundedWorldStep(server,key)){if(boundedWorldActionUsed){SCHEDULER.delay(key,now+1);continue;}boundedWorldActionUsed=true;}execute(server,key,now);}sample(server,now);WishEntityBehaviorManager.tick(server,now);if(now%20==0){expireAttributes(server,now);tickEvents(server,now);}}
    private static void onStarted(ServerStartedEvent event){rebuild(event.getServer());}
    private static void onStopped(ServerStoppedEvent event){SCHEDULER.clear();SLEEPING.clear();DARK.clear();CAVE.clear();YAW.clear();nightPeriod=null;WishEntityBehaviorManager.clear();}
    private static void onDimension(PlayerEvent.PlayerChangedDimensionEvent event){if(event.getEntity() instanceof ServerPlayer player)fireForPlayer(player.server,WishTriggerType.ENTER_DIMENSION,player.getUUID());}
    private static void onDeath(LivingDeathEvent event){if(event.getEntity() instanceof ServerPlayer player)fireForPlayer(player.server,WishTriggerType.PLAYER_DEATH,player.getUUID());if(event.getSource().getEntity() instanceof ServerPlayer killer)fireForPlayer(killer.server,WishTriggerType.PLAYER_KILLS_ENTITY,killer.getUUID());}

    private static void sample(MinecraftServer server,long now){
        if(now%20==0){sampleEnvironment(server,now);long dayTime=Math.floorMod(server.overworld().getDayTime(),24000L);boolean night=dayTime>=13000&&dayTime<23000;if(nightPeriod!=null&&night!=nightPeriod)fireAll(server,night?WishTriggerType.NIGHT_START:WishTriggerType.DAY_START);nightPeriod=night;}
        if(now%5==0){sampleYaw(server,now);sampleEntityConditions(server);}
        Set<UUID> owners=new HashSet<>();for(WishTriggerType type:WishTriggerType.values())for(StepKey key:SCHEDULER.waiting(type)){WishExecutionRecord record=WishExecutionSavedData.get(server).get(key.executionId());if(record!=null)owners.add(record.ownerId());}
        for(UUID owner:owners){ServerPlayer player=server.getPlayerList().getPlayer(owner);if(player==null)continue;boolean sleeping=player.isSleeping(),old=SLEEPING.getOrDefault(owner,false);if(sleeping&&!old)fireForPlayer(server,WishTriggerType.PLAYER_SLEEP,owner);if(!sleeping&&old)fireForPlayer(server,WishTriggerType.PLAYER_WAKE,owner);SLEEPING.put(owner,sleeping);if(player.getHealth()<=player.getMaxHealth()*.25f)fireForPlayer(server,WishTriggerType.PLAYER_LOW_HEALTH,owner);}
    }
    private static void sampleEnvironment(MinecraftServer server,long now){Set<UUID> owners=waitingOwners(server,WishTriggerType.ENTER_DARK_AREA,WishTriggerType.ENTER_CAVE);for(UUID owner:owners){ServerPlayer p=server.getPlayerList().getPlayer(owner);if(p==null)continue;BlockPos pos=p.blockPosition();boolean dark=p.serverLevel().getMaxLocalRawBrightness(pos)<=4,cave=!p.serverLevel().canSeeSky(pos)&&pos.getY()<p.serverLevel().getSeaLevel();if(dark&&!DARK.getOrDefault(owner,false))fireForPlayer(server,WishTriggerType.ENTER_DARK_AREA,owner);if(cave&&!CAVE.getOrDefault(owner,false))fireForPlayer(server,WishTriggerType.ENTER_CAVE,owner);DARK.put(owner,dark);CAVE.put(owner,cave);}}
    private static void sampleYaw(MinecraftServer server,long now){for(UUID owner:waitingOwners(server,WishTriggerType.PLAYER_TURNS_AROUND)){ServerPlayer p=server.getPlayerList().getPlayer(owner);if(p==null)continue;YawSample old=YAW.get(owner);if(old==null){YAW.put(owner,new YawSample(p.getYRot(),now,0));continue;}if(now<old.cooldownUntil)continue;if(now-old.started>40){YAW.put(owner,new YawSample(p.getYRot(),now,0));continue;}float delta=Math.abs(net.minecraft.util.Mth.wrapDegrees(p.getYRot()-old.yaw));if(delta>=90){fireForPlayer(server,WishTriggerType.PLAYER_TURNS_AROUND,owner);YAW.put(owner,new YawSample(p.getYRot(),now,now+100));}}}
    private static void sampleEntityConditions(MinecraftServer server){for(WishTriggerType type:List.of(WishTriggerType.PLAYER_LOOKS_AT_ENTITY,WishTriggerType.ENTITY_NEARBY)){for(StepKey key:SCHEDULER.waiting(type)){WishExecutionRecord record=WishExecutionSavedData.get(server).get(key.executionId());if(record==null)continue;ServerPlayer p=server.getPlayerList().getPlayer(record.ownerId());if(p==null)continue;boolean matches=false;for(UUID id:record.allEntities()){Entity entity=p.serverLevel().getEntity(id);if(entity==null)continue;if(type==WishTriggerType.ENTITY_NEARBY)matches=entity.distanceToSqr(p)<=256;else{Vec3 eye=p.getEyePosition(),look=p.getLookAngle(),to=entity.getBoundingBox().getCenter().subtract(eye);matches=to.lengthSqr()<=4096&&look.dot(to.normalize())>=.985&&entity.getBoundingBox().inflate(.3).clip(eye,eye.add(look.scale(64))).isPresent();}if(matches)break;}if(matches){SCHEDULER.remove(key);triggered(server,key,server.overworld().getGameTime());}}}}
    private static Set<UUID> waitingOwners(MinecraftServer server,WishTriggerType...types){Set<UUID> result=new HashSet<>();for(WishTriggerType type:types)for(StepKey key:SCHEDULER.waiting(type)){WishExecutionRecord r=WishExecutionSavedData.get(server).get(key.executionId());if(r!=null)result.add(r.ownerId());}return result;}
    private static void fireAll(MinecraftServer server,WishTriggerType type){for(StepKey key:SCHEDULER.fire(type,k->true))triggered(server,key,server.overworld().getGameTime());}
    private static void fireForPlayer(MinecraftServer server,WishTriggerType type,UUID player){for(StepKey key:SCHEDULER.fire(type,k->{WishExecutionRecord r=WishExecutionSavedData.get(server).get(k.executionId());return r!=null&&r.ownerId().equals(player);}))triggered(server,key,server.overworld().getGameTime());}
    private static void triggered(MinecraftServer server,StepKey key,long now){WishExecutionRecord record=WishExecutionSavedData.get(server).get(key.executionId());WishRecord wish=record==null?null:WishSavedData.get(server).getBySession(record.wishSessionId());if(record==null||wish==null||wish.plan()==null)return;WishPlanStep planned=wish.plan().steps().get(key.stepIndex());WishStepExecution step=record.step(key.stepIndex());if(step==null||step.state().terminal())return;if(planned.timing()==WishStepTiming.DELAYED_AFTER_TRIGGER){long at=now+planned.delaySeconds()*20L;step.schedule(at);step.transition(WishStepExecutionState.WAITING_DELAY,now);SCHEDULER.delay(key,at);}else{step.transition(WishStepExecutionState.READY,now);SCHEDULER.delay(key,now);}changed(server,record);}

    private static void execute(MinecraftServer server,StepKey key,long now){WishExecutionSavedData data=WishExecutionSavedData.get(server);WishExecutionRecord record=data.get(key.executionId());if(record==null)return;if(record.isProgram()){WishProgramExecutor.executeStep(server,record,key.stepIndex(),now);return;}WishRecord wish=WishSavedData.get(server).getBySession(record.wishSessionId());if(record==null||wish==null||wish.plan()==null)return;WishStepExecution step=record.step(key.stepIndex());if(step==null||step.state().terminal())return;WishPlanStep planned=wish.plan().steps().get(key.stepIndex());ServerPlayer player=server.getPlayerList().getPlayer(record.ownerId());boolean needsPlayer=planned.action()!=WishActionType.CHANGE_TIME&&planned.action()!=WishActionType.CHANGE_WEATHER;if(player==null&&needsPlayer){if(step.targetDeadlineGameTime()>0&&now>=step.targetDeadlineGameTime()){step.transition(WishStepExecutionState.FAILED,now);step.result(WishActionResult.failed("TARGET_TIMEOUT"));finishState(server,record,now);return;}step.transition(WishStepExecutionState.WAITING_TARGET,now);step.schedule(now+20);SCHEDULER.delay(key,now+20);changed(server,record);return;}ServerLevel level=player!=null?player.serverLevel():level(server,wish.dimension());if(level==null){step.transition(WishStepExecutionState.STALE,now);step.result(WishActionResult.stale("LEVEL_NOT_FOUND"));finishState(server,record,now);return;}
        var definition=ACTIONS.definition(planned.action());long timeoutTicks=definition==null?100L:Math.max(1L,definition.timeout().toSeconds()*20L);if(step.startedGameTime()>=0&&now-step.startedGameTime()>=timeoutTicks){step.result(WishActionResult.timeout("ACTION_TIMEOUT",step.affected()));step.transition(WishStepExecutionState.FAILED,now);WishExecutionAudit.transition(record,step.stepIndex(),planned.action(),"TIMEOUT","ACTION_TIMEOUT",step.affected());finishState(server,record,now);return;}WishActionExecutor action=ACTIONS.get(planned.action());WishExecutionContext context=WishExecutionContext.legacy(level,player,wish.plan(),planned,record);WishActionResult validation=action.validate(context);if(validation.status()==WishActionResult.Status.RETRY){step.transition(WishStepExecutionState.WAITING_TARGET,now);step.schedule(now+20);SCHEDULER.delay(key,now+20);changed(server,record);return;}
        boolean firstAttempt=step.startedGameTime()<0;step.transition(WishStepExecutionState.RUNNING,now);record.transition(WishExecutionState.RUNNING,now);changed(server,record);server.overworld().getDataStorage().save();
        if(firstAttempt)WishingWillow.LOGGER.info("Action started session={} id={} parameters={}",record.wishSessionId(),definition==null?planned.action():definition.id(),planned.parameters());
        WishActionResult result=action.execute(context);step.result(result);if(result.successful())step.transition(WishStepExecutionState.SUCCEEDED,now);else if(result.status()==WishActionResult.Status.RETRY){boolean batchContinuation=result.shouldRetryNextTick();if(!batchContinuation&&step.retryCount()>=1){step.retry("LOOP_DETECTED");step.result(WishActionResult.failed("LOOP_DETECTED"));step.transition(WishStepExecutionState.FAILED,now);}else{step.retry(result.code());step.transition(batchContinuation?WishStepExecutionState.WAITING_DELAY:WishStepExecutionState.WAITING_TARGET,now);long next=now+(batchContinuation?1:20);step.schedule(next);SCHEDULER.delay(key,next);}}else if(result.status()==WishActionResult.Status.STALE)step.transition(WishStepExecutionState.STALE,now);else step.transition(WishStepExecutionState.FAILED,now);if(result.status()!=WishActionResult.Status.RETRY||step.state()==WishStepExecutionState.FAILED){int requested=requested(planned,result);var evidence=step.state()==WishStepExecutionState.FAILED&&"LOOP_DETECTED".equals(step.lastError())?WishActionResult.failed("LOOP_DETECTED").toActionResult(requested):result.toActionResult(requested);WishingWillow.LOGGER.info("Action completed session={} id={} status={} requested={} completed={} failed={} message={}",record.wishSessionId(),definition==null?planned.action():definition.id(),evidence.status(),evidence.requested(),evidence.completed(),evidence.failed(),evidence.message());}WishExecutionAudit.transition(record,step.stepIndex(),planned.action(),step.state().name(),step.lastError().isBlank()?result.code():step.lastError(),step.affected());finishState(server,record,now);
    }
    @Nullable static ServerLevel level(MinecraftServer server,ResourceLocation id){return server.getLevel(ResourceKey.create(Registries.DIMENSION,id));}
    private static void finishState(MinecraftServer server,WishExecutionRecord record,long now){
        if(record.isProgram()){WishProgramExecutor.finish(server,record,now);return;}
        WishRecord wish=WishSavedData.get(server).getBySession(record.wishSessionId());
        boolean program=wish!=null&&wish.program()!=null&&wish.plan()!=null;
        if(program){
            List<WishStepExecutionState> core=new ArrayList<>(),presentation=new ArrayList<>();
            for(WishStepExecution step:record.steps()){
                WishPlanStep planned=wish.plan().steps().get(step.stepIndex());
                (planned.batchId().startsWith("wp:presentation")?presentation:core).add(step.state());
            }
            boolean active=record.steps().stream().anyMatch(step->step.state()!=WishStepExecutionState.PENDING
                    &&!step.state().terminal());
            if(!active){
                WishStepExecution pending=record.steps().stream()
                        .filter(step->step.state()==WishStepExecutionState.PENDING).findFirst().orElse(null);
                if(pending!=null){
                    String next=wish.plan().steps().get(pending.stepIndex()).batchId();
                    scheduleProgramGroup(record,wish.plan(),next,now);
                    record.transition(WishExecutionState.SCHEDULED,now);changed(server,record);return;
                }
            }
            if(record.steps().stream().allMatch(step->step.state().terminal()))
                record.transition(WishProgramResultPolicy.reduce(core,presentation),now);
            else record.transition(WishExecutionState.SCHEDULED,now);
            changed(server,record);return;
        }
        boolean all=record.steps().stream().allMatch(s->s.state().terminal());
        boolean anySuccess=record.steps().stream().anyMatch(s->s.state()==WishStepExecutionState.SUCCEEDED);
        boolean anyFailure=record.steps().stream().anyMatch(s->s.state()==WishStepExecutionState.FAILED
                ||s.state()==WishStepExecutionState.STALE||"PARTIAL_SUCCESS".equals(s.lastResult()));
        boolean waiting=record.steps().stream().anyMatch(s->s.state()==WishStepExecutionState.WAITING_TRIGGER);
        boolean partialPlan=wish!=null&&wish.planState()==WishPlanState.PARTIAL;
        if(all){
            if(wish!=null&&wish.interpretation()!=null&&wish.plan()!=null){
                var contract=WishContractValidator.validateActual(wish.interpretation(),wish.plan().steps(),record);
                if(contract.state()==WishContractValidationState.CONTRACT_NOT_FULFILLED){
                    record.fail("CONTRACT_NOT_FULFILLED:"+contract.code()+":"+contract.promisedQuantity(),now);
                    changed(server,record);return;
                }
            }
            record.transition(anyFailure?(anySuccess?WishExecutionState.PARTIAL:WishExecutionState.FAILED)
                    :(partialPlan?WishExecutionState.PARTIAL:WishExecutionState.COMPLETED),now);
        }else if(waiting)record.transition(WishExecutionState.WAITING_TRIGGER,now);
        else record.transition(WishExecutionState.SCHEDULED,now);
        changed(server,record);
    }
    private static void watchdogPrograms(MinecraftServer server,long now){for(WishExecutionRecord record:WishExecutionSavedData.get(server).all()){if(record.state().terminal())continue;if(record.isProgram()){WishProgramExecutor.watchdog(server,record,now);continue;}WishRecord wish=WishSavedData.get(server).getBySession(record.wishSessionId());if(wish==null||wish.program()==null)continue;long timeout=wish.program().usesSkill()?1200L:1800L;if(now-record.createdGameTime()<timeout)continue;for(WishStepExecution step:record.steps())if(!step.state().terminal()){SCHEDULER.remove(new StepKey(record.executionId(),step.stepIndex()));step.result(WishActionResult.timeout("PROGRAM_TIMEOUT",step.affected()));step.transition(WishStepExecutionState.FAILED,now);}record.fail(wish.program().usesSkill()?"SKILL_TIMEOUT":"PROGRAM_TIMEOUT",now);changed(server,record);}}
    static void changed(MinecraftServer server,WishExecutionRecord record){WishExecutionSavedData.get(server).changed();WishSavedData wishes=WishSavedData.get(server);WishRecord wish=wishes.getBySession(record.wishSessionId());if(wish!=null){WishExecutionAcceptError error=record.state()==WishExecutionState.STALE?WishExecutionAcceptError.STALE_RESOURCE:record.state()==WishExecutionState.FAILED?WishExecutionAcceptError.UNKNOWN:WishExecutionAcceptError.NONE;String detail=error==WishExecutionAcceptError.NONE?"":runtimeDetail(record);boolean newlyTerminal=record.state().terminal()&&!wish.executionState().terminal();wishes.update(wish.withExecution(record.executionId(),record.state(),error,detail));if(record.state().terminal()){var summary=WishProgramResultPolicy.summarize(record.steps().stream().limit(record.coreActionCount()).map(WishStepExecution::state).toList(),record.steps().stream().skip(record.coreActionCount()).map(WishStepExecution::state).toList());WishingWillow.LOGGER.info("Wish execution outcome session={} outcome={} coreSuccess={} coreFailed={} presentationSuccess={} presentationFailed={}",record.wishSessionId(),summary.outcome(),summary.coreSuccess(),summary.coreFailed(),summary.presentationSuccess(),summary.presentationFailed());WishPipelineAudit.execution(record.wishSessionId(),record.planId(),record.executionId(),record.state().name(),error.name(),detail);if(newlyTerminal){com.ikunkk02.wishingwillow.advancement.WishAdvancementManager.onExecutionCompleted(server,record,wish);ServerPlayer player=server.getPlayerList().getPlayer(record.ownerId());if(player!=null){boolean completed=record.state()==WishExecutionState.COMPLETED;WishPipelineState state=record.state()==WishExecutionState.PARTIAL?WishPipelineState.PARTIAL_SUCCESS:record.state()==WishExecutionState.UNEXECUTABLE?WishPipelineState.UNEXECUTABLE:completed?WishPipelineState.COMPLETED:WishPipelineState.FAILED;WishSessionTerminationReason reason=completed?WishSessionTerminationReason.EXECUTION_COMPLETE:record.state()==WishExecutionState.PARTIAL?WishSessionTerminationReason.EXECUTION_PARTIAL:record.state()==WishExecutionState.UNEXECUTABLE?WishSessionTerminationReason.EXECUTION_UNEXECUTABLE:WishSessionTerminationReason.EXECUTION_FAILED;ModNetworking.sendToPlayer(player,WishPipelineStatePacket.terminal(record.wishSessionId(),state,reason,""));player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(state==WishPipelineState.PARTIAL_SUCCESS?"message.wishing_willow.wish_partial_success":state==WishPipelineState.UNEXECUTABLE?"message.wishing_willow.wish_unexecutable":completed?"message.wishing_willow.wish_success":"message.wishing_willow.wish_failed"));WishLifecycleLog.event(record.wishSessionId(),"EXECUTION_COMPLETED","execution="+record.executionId()+" outcome="+summary.outcome());WishLifecycleLog.event(record.wishSessionId(),"SESSION_TERMINATED","reason="+reason);}}}}}
    private static String runtimeDetail(WishExecutionRecord record){return record.steps().stream().filter(step->step.state()==WishStepExecutionState.FAILED||step.state()==WishStepExecutionState.STALE).map(step->"step="+step.stepIndex()+" result="+step.lastResult()).findFirst().orElse(record.lastError());}
    private static int requested(WishPlanStep step,WishActionResult result){return step.parameters().has("count")?Math.max(0,step.parameters().get("count").getAsInt()):Math.max(1,result.affected());}
    private static boolean isBoundedWorldStep(MinecraftServer server,StepKey key){WishExecutionRecord record=WishExecutionSavedData.get(server).get(key.executionId());if(record==null)return false;if(record.isProgram())return WishProgramExecutor.isBoundedWorldStep(server,record,key.stepIndex());WishRecord wish=WishSavedData.get(server).getBySession(record.wishSessionId());if(wish==null||wish.plan()==null)return false;WishActionType action=wish.plan().steps().get(key.stepIndex()).action();return action==WishActionType.CHANGE_BLOCK||action==WishActionType.REPLACE_BLOCK_AREA||action==WishActionType.PLACE_BLOCK_PATTERN||action==WishActionType.FALLING_BLOCK_SHOWER||action==WishActionType.ITEM_RAIN||action==WishActionType.CREATE_STRUCTURE;}
    private static void expireAttributes(MinecraftServer server,long now){for(WishExecutionRecord record:WishExecutionSavedData.get(server).all()){if(record.attributeLeases().isEmpty())continue;ServerPlayer player=server.getPlayerList().getPlayer(record.ownerId());if(player==null)continue;for(var entry:record.attributeLeases().entrySet()){var lease=entry.getValue();if(now<lease.expires())continue;Attribute attribute=switch(lease.attribute()){case "MAX_HEALTH"->Attributes.MAX_HEALTH;case "MOVEMENT_SPEED"->Attributes.MOVEMENT_SPEED;case "ATTACK_DAMAGE"->Attributes.ATTACK_DAMAGE;case "ARMOR"->Attributes.ARMOR;case "KNOCKBACK_RESISTANCE"->Attributes.KNOCKBACK_RESISTANCE;default->Attributes.LUCK;};AttributeInstance instance=player.getAttribute(attribute);if(instance!=null)instance.removeModifier(lease.modifier());record.removeLease(entry.getKey());WishExecutionSavedData.get(server).changed();}}}
    private static void tickEvents(MinecraftServer server,long now){for(WishExecutionRecord record:WishExecutionSavedData.get(server).all()){for(var entry:record.eventLeases().entrySet()){if(now>=entry.getValue()){if(PredefinedWishEventRegistry.isStalkerLease(entry.getKey())&&server.getPlayerList().getPlayer(record.ownerId())==null)continue;record.removeEventLease(entry.getKey());WishExecutionSavedData.get(server).changed();server.overworld().getDataStorage().save();if(PredefinedWishEventRegistry.isStalkerLease(entry.getKey()))runStalkerPhase(server,record,PredefinedWishEventRegistry.stalkerStep(entry.getKey()),now);continue;}if(entry.getKey().equals(PredefinedWishEventRegistry.ENDLESS_NIGHT)){ServerPlayer player=server.getPlayerList().getPlayer(record.ownerId());ServerLevel level=player==null?server.overworld():player.serverLevel();long day=level.getDayTime()/24000L*24000L;level.setDayTime(day+18000);}}}}
    private static void runStalkerPhase(MinecraftServer server,WishExecutionRecord record,int stepIndex,long now){ServerPlayer player=server.getPlayerList().getPlayer(record.ownerId());WishRecord wish=WishSavedData.get(server).getBySession(record.wishSessionId());if(player==null||wish==null||wish.plan()==null||stepIndex<0||stepIndex>=wish.plan().steps().size())return;WishPlanStep event=wish.plan().steps().get(stepIndex);int intensity=Math.max(1,Math.min(5,event.parameters().get("intensity").getAsInt()));VerifiedRegistryResource resource=new VerifiedRegistryResource(RegistryEntryType.ENTITY,"minecraft:zombie");CandidateReference candidate=new CandidateReference("internal-stalker",WishCapability.STALKING_ENTITY,WishCapability.STALKING_ENTITY,MatchType.EXACT,CandidateSourceKind.VANILLA_REGISTRY,"minecraft","1.20.1","minecraft:zombie",FeatureType.ENTITY,resource,100,60);WishPlanStep spawn=new WishPlanStep(stepIndex,WishStepTiming.IMMEDIATE,0,WishTriggerType.NONE,WishActionType.SPAWN_ENTITY,WishCapability.STALKING_ENTITY,"internal-stalker",WishTargetType.PLAYER,JsonParser.parseString("{\"count\":1,\"distance_min\":12,\"distance_max\":24}").getAsJsonObject(),"predefined event phase",candidate);Set<UUID> before=new HashSet<>(record.allEntities());WishActionResult result=ACTIONS.get(WishActionType.SPAWN_ENTITY).execute(WishExecutionContext.legacy(player.serverLevel(),player,wish.plan(),spawn,record));if(result.successful()){for(UUID entity:record.entitiesForStep(stepIndex))if(!before.contains(entity)){long expires=now+intensity*1200L;record.leaseBehavior(entity,WishEntityBehaviorManager.Mode.FOLLOW.name(),1.05,8,expires);WishEntityBehaviorManager.bind(record.executionId(),entity,player.getUUID(),WishEntityBehaviorManager.Mode.FOLLOW,1.05,8,expires);}}WishExecutionSavedData.get(server).changed();WishExecutionAudit.transition(record,stepIndex,WishActionType.START_PREDEFINED_EVENT,result.status().name(),result.code(),result.affected());}
    private record YawSample(float yaw,long started,long cooldownUntil){}
}
