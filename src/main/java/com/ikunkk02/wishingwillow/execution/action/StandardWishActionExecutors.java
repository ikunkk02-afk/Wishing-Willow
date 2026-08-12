package com.ikunkk02.wishingwillow.execution.action;

import com.google.gson.JsonObject;
import com.ikunkk02.wishingwillow.WishingWillow;
import com.ikunkk02.wishingwillow.execution.*;
import com.ikunkk02.wishingwillow.planning.WishTargetType;
import com.ikunkk02.wishingwillow.research.VerifiedRegistryResource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.ai.gossip.GossipType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * All executors consume only the native {@link WishExecutionContext} (action id, canonical
 * parameters, target, capability, candidate, execution record). No executor references
 * {@code WishPlanStep} or {@code WishPlan} — legacy saved plans are adapted into the native
 * context shape by the legacy executor (OLD to NEW), never the reverse.
 */
final class StandardWishActionExecutors {
    private StandardWishActionExecutors(){}
    private interface Action { WishActionResult run(WishExecutionContext c); }
    private static WishActionExecutor executor(Action action){return new WishActionExecutor(){public WishActionResult validate(WishExecutionContext c){return basic(c);}public WishActionResult execute(WishExecutionContext c){try{return action.run(c);}catch(Throwable error){WishingWillow.LOGGER.error("Wish action isolated: execution={} step={} action={} error={}",c.execution().executionId(),c.stepIndex(),c.actionId(),error.getClass().getSimpleName());return WishActionResult.failed("ACTION_EXCEPTION_"+error.getClass().getSimpleName());}}};}
    private static WishActionResult basic(WishExecutionContext c){if(c.player()==null&&c.target()!=WishTargetType.WORLD)return WishActionResult.retry("WAITING_TARGET");return WishActionResult.success(0);}
    private static ResourceLocation id(WishExecutionContext c){VerifiedRegistryResource r=c.candidate()==null?null:c.candidate().registryResource();return r==null?null:ResourceLocation.tryParse(r.id());}
    private static int i(JsonObject p,String k){return p.get(k).getAsInt();}private static double d(JsonObject p,String k){return p.get(k).getAsDouble();}private static boolean b(JsonObject p,String k){return p.get(k).getAsBoolean();}

    static WishActionExecutor giveItem(){return executor(c->{ServerPlayer p=c.player();Item item=ForgeRegistries.ITEMS.getValue(id(c));if(item==null)return WishActionResult.stale("ITEM_NOT_FOUND");int left=i(c.parameters(),"count"),given=0;while(left>0){int amount=Math.min(left,item.getMaxStackSize());ItemStack stack=new ItemStack(item,amount);p.getInventory().add(stack);int accepted=amount-stack.getCount();given+=accepted;left-=amount;if(!stack.isEmpty()){p.drop(stack,false);given+=stack.getCount();}}return WishActionResult.success(given);});}
    static WishActionExecutor removeItem(){return executor(c->{ServerPlayer p=c.player();Item item=ForgeRegistries.ITEMS.getValue(id(c));if(item==null)return WishActionResult.stale("ITEM_NOT_FOUND");int wanted=i(c.parameters(),"count"),removed=0;for(int slot=0;slot<p.getInventory().getContainerSize()&&removed<wanted;slot++){ItemStack stack=p.getInventory().getItem(slot);if(!stack.is(item))continue;int take=Math.min(stack.getCount(),wanted-removed);stack.shrink(take);removed+=take;}return removed==wanted?WishActionResult.success(removed):WishActionResult.partial("INSUFFICIENT_ITEMS",removed);});}
    static WishActionExecutor spawnEntity(){return executor(c->{ResourceLocation resource=id(c);EntityType<?> type=ForgeRegistries.ENTITY_TYPES.getValue(resource);if(type==null)return WishActionResult.stale("ENTITY_NOT_FOUND");if(!resource.getNamespace().equals("minecraft")&&!WishExecutionConfig.THIRD_PARTY_ENTITIES.get())return WishActionResult.failed("THIRD_PARTY_ENTITIES_DISABLED");ServerPlayer player=c.player();int count=i(c.parameters(),"count"),spawned=0;for(int n=0;n<count;n++){Entity entity;try{entity=type.create(c.level());}catch(Throwable error){return spawned>0?WishActionResult.partial("ENTITY_CREATE_FAILED",spawned):WishActionResult.failed("ENTITY_CREATE_FAILED");}if(entity==null)return spawned>0?WishActionResult.partial("ENTITY_CREATE_NULL",spawned):WishActionResult.failed("ENTITY_CREATE_FAILED");Vec3 pos=SafeSpawnPositionFinder.find(c.level(),entity,player.position(),i(c.parameters(),"distance_min"),i(c.parameters(),"distance_max"),c.capability()==com.ikunkk02.wishingwillow.ai.WishCapability.STALKING_ENTITY,player.getYRot(),c.execution().executionId().getLeastSignificantBits()+c.stepIndex()*31L+n);if(pos==null)continue;entity.moveTo(pos.x,pos.y,pos.z,player.getYRot()+180,0);if(c.capability()==com.ikunkk02.wishingwillow.ai.WishCapability.PERSISTENT_FOLLOWER||c.capability()==com.ikunkk02.wishingwillow.ai.WishCapability.FRIENDLY_ENTITY){if(entity instanceof Mob mob)mob.setPersistenceRequired();if(entity instanceof TamableAnimal tame)tame.tame(player);}try{if(c.level().addFreshEntity(entity)){c.execution().bindEntity(c.stepIndex(),entity.getUUID());spawned++;}}catch(Throwable ignored){entity.discard();}}return spawned==count?WishActionResult.success(spawned):spawned>0?WishActionResult.partial("SAFE_POSITION_OR_SPAWN_FAILED",spawned):WishActionResult.failed("ENTITY_SPAWN_FAILED");});}
    static WishActionExecutor despawnEntity(){return executor(c->{EntityType<?> expected=ForgeRegistries.ENTITY_TYPES.getValue(id(c));int max=i(c.parameters(),"max_count"),removed=0;for(UUID uuid:c.execution().allEntities()){Entity entity=c.level().getEntity(uuid);if(entity!=null&&entity.getType()==expected&&entity.distanceToSqr(c.player())<=Math.pow(i(c.parameters(),"radius"),2)&&removed<max){entity.discard();removed++;}}return WishActionResult.success(removed);});}
    static WishActionExecutor applyEffect(){return executor(c->{MobEffect effect=ForgeRegistries.MOB_EFFECTS.getValue(id(c));if(effect==null)return WishActionResult.stale("EFFECT_NOT_FOUND");boolean ok=c.player().addEffect(new MobEffectInstance(effect,i(c.parameters(),"duration_seconds")*20,i(c.parameters(),"amplifier")));return ok?WishActionResult.success(1):WishActionResult.failed("EFFECT_REJECTED");});}
    static WishActionExecutor removeEffect(){return executor(c->{MobEffect effect=ForgeRegistries.MOB_EFFECTS.getValue(id(c));if(effect==null)return WishActionResult.stale("EFFECT_NOT_FOUND");return WishActionResult.success(c.player().removeEffect(effect)?1:0);});}
    static WishActionExecutor clearEffects(){return executor(c->{int before=c.player().getActiveEffects().size();c.player().removeAllEffects();return WishActionResult.success(before);});}
    static WishActionExecutor applyEffectCategory(){return executor(c->{
        MobEffectCategory category=switch(c.parameters().get("category").getAsString()){
            case "BENEFICIAL"->MobEffectCategory.BENEFICIAL;case "HARMFUL"->MobEffectCategory.HARMFUL;default->MobEffectCategory.NEUTRAL;};
        int duration=i(c.parameters(),"duration_seconds"),amplifier=i(c.parameters(),"amplifier"),applied=0;
        for(MobEffect effect:List.copyOf(ForgeRegistries.MOB_EFFECTS.getValues())){
            if(effect.getCategory()!=category)continue;
            try{
                if(effect.isInstantenous()){
                    effect.applyInstantenousEffect(c.player(),c.player(),c.player(),amplifier,1.0);applied++;
                }else if(c.player().addEffect(new MobEffectInstance(effect,duration*20,amplifier))
                        ||c.player().hasEffect(effect))applied++;
            }catch(Throwable error){
                WishingWillow.LOGGER.warn("Category effect skipped category={} id={} error={}",category,
                        ForgeRegistries.MOB_EFFECTS.getKey(effect),error.getClass().getSimpleName());
            }
        }
        return applied>0?WishActionResult.success(applied):WishActionResult.failed("NO_EFFECTS_IN_CATEGORY");
    });}
    static WishActionExecutor teleport(){return executor(c->{ServerPlayer player=c.player();JsonObject p=c.parameters();String mode=p.get("mode").getAsString();ServerLevel target=c.level();Vec3 pos;if(mode.equals("CANDIDATE_DIMENSION")){if(!WishExecutionConfig.CROSS_DIMENSION_TELEPORT.get())return WishActionResult.failed("CROSS_DIMENSION_DISABLED");ResourceLocation dimension=id(c);target=player.server.getLevel(ResourceKey.create(Registries.DIMENSION,dimension));if(target==null)return WishActionResult.stale("DIMENSION_NOT_FOUND");BlockPos spawn=target.getSharedSpawnPos();target.getChunk(spawn);pos=SafeSpawnPositionFinder.findPlayer(target,Vec3.atCenterOf(spawn),2,32,c.execution().executionId().getMostSignificantBits());}else pos=SafeSpawnPositionFinder.findPlayer(target,player.position(),i(p,"distance_min"),i(p,"distance_max"),c.execution().executionId().getMostSignificantBits());if(pos==null)return WishActionResult.failed("NO_SAFE_TELEPORT_POSITION");player.teleportTo(target,pos.x,pos.y,pos.z,player.getYRot(),player.getXRot());return WishActionResult.success(1);});}
    static WishActionExecutor changeTime(){return executor(c->{long day=c.level().getDayTime()/24000L*24000L;long value=switch(c.parameters().get("value").getAsString()){case "DAY"->1000;case "NIGHT"->13000;case "DAWN"->0;default->12000;};c.level().setDayTime(day+value);return WishActionResult.success(1);});}
    static WishActionExecutor changeWeather(){return executor(c->{String weather=c.parameters().get("weather").getAsString();int ticks=i(c.parameters(),"duration_seconds")*20;c.level().setWeatherParameters(weather.equals("CLEAR")?ticks:0,weather.equals("CLEAR")?0:ticks,!weather.equals("CLEAR"),weather.equals("THUNDER"));return WishActionResult.success(1);});}
    static WishActionExecutor playSound(){return executor(c->{SoundEvent sound=ForgeRegistries.SOUND_EVENTS.getValue(id(c));if(sound==null)return WishActionResult.stale("SOUND_NOT_FOUND");ServerPlayer p=c.player();c.level().playSound(null,p.getX(),p.getY(),p.getZ(),sound,SoundSource.AMBIENT,(float)d(c.parameters(),"volume"),(float)d(c.parameters(),"pitch"));return WishActionResult.success(1);});}
    static WishActionExecutor spawnParticle(){return executor(c->{ParticleType<?> type=ForgeRegistries.PARTICLE_TYPES.getValue(id(c));if(!(type instanceof SimpleParticleType simple))return WishActionResult.unsupported("COMPLEX_PARTICLE_UNSUPPORTED");ServerPlayer p=c.player();double radius=d(c.parameters(),"radius");int sent=c.level().sendParticles(simple,p.getX(),p.getEyeY(),p.getZ(),i(c.parameters(),"count"),radius,radius,radius,0.02);return WishActionResult.success(sent);});}
    static WishActionExecutor lightning(){return executor(c->{ServerPlayer p=c.player();int spawned=0;for(int n=0;n<i(c.parameters(),"count");n++){LightningBolt bolt=EntityType.LIGHTNING_BOLT.create(c.level());if(bolt==null)continue;Vec3 pos=SafeSpawnPositionFinder.find(c.level(),bolt,p.position(),i(c.parameters(),"distance_min"),i(c.parameters(),"distance_max"),false,p.getYRot(),n+c.execution().executionId().getLeastSignificantBits());if(pos!=null){bolt.moveTo(pos);if(c.level().addFreshEntity(bolt))spawned++;}}return WishActionResult.success(spawned);});}
    static WishActionExecutor explosion(){return executor(c->{if(!WishExecutionConfig.EXPLOSIONS.get())return WishActionResult.failed("EXPLOSIONS_DISABLED");double power=d(c.parameters(),"power");if(!WishExecutionSafety.validExplosionPower(power))return WishActionResult.failed("INVALID_EXPLOSION_POWER");boolean destroy=b(c.parameters(),"destroy_blocks");if(WishExecutionConfig.DEBUG_SAFE_MODE.get()&&(destroy||power>2))return WishActionResult.failed("DEBUG_SAFE_MODE");if(destroy&&!WishExecutionConfig.DESTRUCTIVE_EXPLOSIONS.get())return WishActionResult.failed("DESTRUCTIVE_EXPLOSIONS_DISABLED");ServerPlayer p=c.player();double angle=(c.execution().executionId().getLeastSignificantBits()&1023)/1023.0*Math.PI*2,dist=i(c.parameters(),"distance_min");double x=p.getX()+Math.cos(angle)*dist,z=p.getZ()+Math.sin(angle)*dist;BlockPos pos=BlockPos.containing(x,p.getY(),z);if(destroy&&p.server.isUnderSpawnProtection(c.level(),pos,p))return WishActionResult.failed("SPAWN_PROTECTION");c.level().explode(p,x,p.getY(),z,(float)power,destroy?Level.ExplosionInteraction.BLOCK:Level.ExplosionInteraction.NONE);return WishActionResult.success(1);});}
    static WishActionExecutor changeBlock(){return executor(c->{if(!WishExecutionConfig.BLOCK_MODIFICATION.get())return WishActionResult.failed("BLOCK_MODIFICATION_DISABLED");if(WishExecutionConfig.DEBUG_SAFE_MODE.get())return WishActionResult.failed("DEBUG_SAFE_MODE");Block block=ForgeRegistries.BLOCKS.getValue(id(c));if(block==null)return WishActionResult.stale("BLOCK_NOT_FOUND");WishWorldChangeJournal journal=c.execution().journal(c.stepIndex());if(!journal.prepared()){Vec3 pos=SafeSpawnPositionFinder.findPlayer(c.level(),c.player().position(),i(c.parameters(),"distance_min"),i(c.parameters(),"distance_max"),c.execution().executionId().getLeastSignificantBits());if(pos==null)return WishActionResult.failed("NO_SAFE_BLOCK_POSITION");BlockPos target=BlockPos.containing(pos).below();if(c.level().getBlockEntity(target)!=null)return WishActionResult.failed("BLOCK_ENTITY_SKIPPED");journal.add(target,c.level().getBlockState(target),block.defaultBlockState());}int changed=0;for(WishWorldChangeJournal.Entry entry:journal.next(1))if(c.level().setBlock(entry.position(),block.defaultBlockState(),3))changed++;journal.advance(1);return WishActionResult.success(changed);});}
    static WishActionExecutor replaceBlockArea(){return executor(c->{if(!WishExecutionConfig.BLOCK_MODIFICATION.get())return WishActionResult.failed("BLOCK_MODIFICATION_DISABLED");if(WishExecutionConfig.DEBUG_SAFE_MODE.get())return WishActionResult.failed("DEBUG_SAFE_MODE");Block block=ForgeRegistries.BLOCKS.getValue(id(c));if(block==null)return WishActionResult.stale("BLOCK_NOT_FOUND");WishWorldChangeJournal journal=c.execution().journal(c.stepIndex());if(!journal.prepared()){BlockPos center=c.player().blockPosition();int radius=i(c.parameters(),"radius"),limit=Math.min(2048,i(c.parameters(),"max_blocks"));for(BlockPos pos:BlockPos.betweenClosed(center.offset(-radius,-radius,-radius),center.offset(radius,radius,radius))){if(journal.size()>=limit)break;BlockPos immutable=pos.immutable();if(!c.level().hasChunkAt(immutable)||c.level().getBlockEntity(immutable)!=null)continue;BlockState old=c.level().getBlockState(immutable);if(old.isAir()||old==block.defaultBlockState())continue;journal.add(immutable,old,block.defaultBlockState());}}int processed=0,changed=0;for(WishWorldChangeJournal.Entry entry:journal.next(128)){processed++;if(c.level().setBlock(entry.position(),block.defaultBlockState(),3))changed++;}journal.advance(processed);return journal.complete()?WishActionResult.success(journal.size()):WishActionResult.retryNextTick();});}
    static WishActionExecutor placeBlockPattern(){return executor(c->{
        if(!WishExecutionConfig.BLOCK_MODIFICATION.get())return WishActionResult.failed("BLOCK_MODIFICATION_DISABLED");
        if(WishExecutionConfig.DEBUG_SAFE_MODE.get())return WishActionResult.failed("DEBUG_SAFE_MODE");
        Block block=ForgeRegistries.BLOCKS.getValue(id(c));if(block==null)return WishActionResult.stale("BLOCK_NOT_FOUND");
        WishWorldChangeJournal journal=c.execution().journal(c.stepIndex());
        if(!journal.prepared()){
            int count=i(c.parameters(),"count");String pattern=c.parameters().get("pattern").getAsString();
            List<BlockPos> positions=patternPositions(c.player().blockPosition(),pattern,count);
            if(positions.size()!=count)return WishActionResult.failed("PATTERN_CAPACITY_SHORT");
            for(BlockPos pos:positions){
                if(pos.getY()<c.level().getMinBuildHeight()||pos.getY()>=c.level().getMaxBuildHeight()
                        ||!c.level().hasChunkAt(pos)||c.level().getBlockEntity(pos)!=null
                        ||c.player().server.isUnderSpawnProtection(c.level(),pos,c.player()))return WishActionResult.failed("PATTERN_PREVALIDATION_FAILED");
            }
            for(BlockPos pos:positions)journal.add(pos,c.level().getBlockState(pos),block.defaultBlockState());
        }
        int processed=0;for(WishWorldChangeJournal.Entry entry:journal.next(128)){processed++;c.level().setBlock(entry.position(),block.defaultBlockState(),3);}
        journal.advance(processed);return journal.complete()?WishActionResult.success(journal.size()):WishActionResult.retryNextTick();
    });}
    static WishActionExecutor createStructure(){return executor(c->{
        if(!WishExecutionConfig.BLOCK_MODIFICATION.get())return WishActionResult.failed("BLOCK_MODIFICATION_DISABLED");
        if(WishExecutionConfig.DEBUG_SAFE_MODE.get())return WishActionResult.failed("DEBUG_SAFE_MODE");
        WishWorldChangeJournal journal=c.execution().journal(c.stepIndex());Block block=Blocks.OAK_PLANKS;
        if(!journal.prepared()){
            List<BlockPos> positions=simpleHousePositions(c.player().blockPosition());
            for(BlockPos pos:positions){if(pos.getY()<c.level().getMinBuildHeight()||pos.getY()>=c.level().getMaxBuildHeight()
                    ||!c.level().hasChunkAt(pos)||c.level().getBlockEntity(pos)!=null
                    ||c.player().server.isUnderSpawnProtection(c.level(),pos,c.player()))return WishActionResult.failed("STRUCTURE_PREVALIDATION_FAILED");}
            for(BlockPos pos:positions)journal.add(pos,c.level().getBlockState(pos),block.defaultBlockState());
        }
        int processed=0;for(WishWorldChangeJournal.Entry entry:journal.next(512)){processed++;c.level().setBlock(entry.position(),block.defaultBlockState(),3);}
        journal.advance(processed);return journal.complete()?WishActionResult.success(journal.size()):WishActionResult.retryNextTick();
    });}
    private static List<BlockPos> patternPositions(BlockPos center,String pattern,int count){
        LinkedHashSet<BlockPos> result=new LinkedHashSet<>();
        if("PILLAR".equals(pattern)){
            for(int n=0;n<count;n++)result.add(center.offset(0,n-1,0));
        }else{
            for(int radius=2;result.size()<count&&radius<=16;radius++){
                int vertical=radius;
                for(int y=-vertical;y<=vertical&&result.size()<count;y++)for(int x=-radius;x<=radius&&result.size()<count;x++)for(int z=-radius;z<=radius&&result.size()<count;z++){
                    if(Math.max(Math.max(Math.abs(x),Math.abs(z)),Math.abs(y))!=radius)continue;
                    result.add(center.offset(x,y+1,z));
                }
            }
            for(int radius=1;result.size()<count&&radius<=16;radius++)for(int x=-radius;x<=radius&&result.size()<count;x++)for(int z=-radius;z<=radius&&result.size()<count;z++){
                if(x==0&&z==0)continue;result.add(center.offset(x,-1,z));
            }
        }
        return result.stream().limit(count).toList();
    }
    private static List<BlockPos> simpleHousePositions(BlockPos center){
        LinkedHashSet<BlockPos> result=new LinkedHashSet<>();
        for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++)result.add(center.offset(x,-1,z));
        for(int y=0;y<=2;y++)for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++){
            if(Math.abs(x)!=3&&Math.abs(z)!=3)continue;
            if(z==-3&&x==0&&y<=1)continue;
            result.add(center.offset(x,y,z));
        }
        for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++)result.add(center.offset(x,3,z));
        return List.copyOf(result);
    }
    static WishActionExecutor modifyHealth(){return executor(c->{ServerPlayer p=c.player();float delta=(float)d(c.parameters(),"delta"),next=Math.min(p.getMaxHealth(),p.getHealth()+delta);if(!b(c.parameters(),"allow_lethal"))next=Math.max(1,next);else next=Math.max(0,next);p.setHealth(next);return WishActionResult.success(1);});}
    static WishActionExecutor modifyHunger(){return executor(c->{ServerPlayer p=c.player();int food=Math.max(0,Math.min(20,p.getFoodData().getFoodLevel()+i(c.parameters(),"delta")));p.getFoodData().setFoodLevel(food);p.getFoodData().setSaturation(Math.min(p.getFoodData().getSaturationLevel(),food));return WishActionResult.success(1);});}
    static WishActionExecutor modifyAttribute(){return executor(c->{ServerPlayer p=c.player();String name=c.parameters().get("attribute").getAsString();Attribute attribute=switch(name){case "MAX_HEALTH"->Attributes.MAX_HEALTH;case "MOVEMENT_SPEED"->Attributes.MOVEMENT_SPEED;case "ATTACK_DAMAGE"->Attributes.ATTACK_DAMAGE;case "ARMOR"->Attributes.ARMOR;case "KNOCKBACK_RESISTANCE"->Attributes.KNOCKBACK_RESISTANCE;default->Attributes.LUCK;};AttributeInstance instance=p.getAttribute(attribute);if(instance==null)return WishActionResult.unsupported("ATTRIBUTE_UNAVAILABLE");UUID uuid=WishExecutionSafety.stableAttributeModifierId(c.execution().executionId(),c.stepIndex(),name);if(instance.getModifier(uuid)!=null)return WishActionResult.success(0);AttributeModifier.Operation operation=c.parameters().get("operation").getAsString().equals("ADD")?AttributeModifier.Operation.ADDITION:AttributeModifier.Operation.MULTIPLY_TOTAL;instance.addPermanentModifier(new AttributeModifier(uuid,"Wishing Willow "+c.execution().executionId(),d(c.parameters(),"amount"),operation));c.execution().leaseAttribute(c.stepIndex(),name,uuid,c.level().getGameTime()+i(c.parameters(),"duration_seconds")*20L);return WishActionResult.success(1);});}
    static WishActionExecutor changeMobTarget(){return executor(c->{String disposition=c.parameters().get("disposition").getAsString();int affected=0,max=i(c.parameters(),"max_entities"),radius=i(c.parameters(),"radius");for(UUID uuid:c.execution().allEntities()){if(affected>=max)break;Entity entity=c.level().getEntity(uuid);if(!(entity instanceof Mob mob)||entity.distanceToSqr(c.player())>radius*radius)continue;if(disposition.equals("CLEAR"))mob.setTarget(null);else if(disposition.equals("PLAYER")){mob.setTarget(c.player());c.execution().leaseBehavior(uuid,WishEntityBehaviorManager.Mode.TARGET.name(),1,0,c.level().getGameTime()+1200);WishEntityBehaviorManager.bind(c.execution().executionId(),uuid,c.player().getUUID(),WishEntityBehaviorManager.Mode.TARGET,1,0,c.level().getGameTime()+1200);}else{net.minecraft.world.entity.monster.Monster nearest=c.level().getEntitiesOfClass(net.minecraft.world.entity.monster.Monster.class,mob.getBoundingBox().inflate(radius),other->other!=mob).stream().min(Comparator.comparingDouble(mob::distanceToSqr)).orElse(null);mob.setTarget(nearest);}affected++;}return affected>0?WishActionResult.success(affected):WishActionResult.retry("WAITING_BOUND_ENTITY");});}
    static WishActionExecutor followPlayer(){return behavior(WishEntityBehaviorManager.Mode.FOLLOW);}
    static WishActionExecutor avoidPlayer(){return behavior(WishEntityBehaviorManager.Mode.AVOID);}
    private static WishActionExecutor behavior(WishEntityBehaviorManager.Mode mode){return executor(c->{int affected=0,duration=c.parameters().has("duration_seconds")?i(c.parameters(),"duration_seconds"):600,max=c.parameters().has("max_entities")?i(c.parameters(),"max_entities"):8;for(UUID uuid:c.execution().allEntities()){if(affected>=max)break;Entity entity=c.level().getEntity(uuid);if(entity instanceof Mob){double min=c.parameters().has("radius")?i(c.parameters(),"radius"):16;long expires=c.level().getGameTime()+duration*20L;c.execution().leaseBehavior(uuid,mode.name(),1.1,min,expires);WishEntityBehaviorManager.bind(c.execution().executionId(),uuid,c.player().getUUID(),mode,1.1,min,expires);affected++;}}return affected>0?WishActionResult.success(affected):WishActionResult.retry("WAITING_BOUND_ENTITY");});}
    static WishActionExecutor changeReputation(){return executor(c->{int delta=i(c.parameters(),"delta");if(delta>0){int affected=WishPersistentSocialRules.grant(c.player(),delta);return WishActionResult.success(affected);}int affected=0,radius=i(c.parameters(),"radius");for(Villager villager:c.level().getEntitiesOfClass(Villager.class,c.player().getBoundingBox().inflate(radius),v->true)){villager.getGossips().add(c.player().getUUID(),GossipType.MINOR_NEGATIVE,Math.abs(delta));affected++;if(affected>=16)break;}return affected>0?WishActionResult.success(affected):WishActionResult.unsupported("NO_VANILLA_VILLAGER");});}
    static WishActionExecutor predefinedEvent(){return executor(c->{
        String event=c.candidate()==null?null:c.candidate().featureName();int intensity=i(c.parameters(),"intensity");
        if(!PredefinedWishEventRegistry.contains(event))return WishActionResult.unsupported("EVENT_NOT_REGISTERED");
        ServerPlayer p=c.player();if(p==null)return WishActionResult.retry("WAITING_TARGET");
        if(event.equals(PredefinedWishEventRegistry.ALL_POSITIVE_EFFECTS)){
            int durationSeconds=Math.min(3600,Math.max(60,intensity*720));int amplifier=Math.min(4,intensity-1);int applied=0;
            for(MobEffect effect:List.copyOf(ForgeRegistries.MOB_EFFECTS.getValues())){
                if(effect.getCategory()!= MobEffectCategory.BENEFICIAL)continue;
                if(effect.isInstantenous()){
                    effect.applyInstantenousEffect(null,null,p,amplifier,1.0);applied++;
                }else if(p.addEffect(new MobEffectInstance(effect,durationSeconds*20,amplifier))){
                    applied++;
                }
            }
            return applied>0?WishActionResult.success(applied):WishActionResult.failed("NO_BENEFICIAL_EFFECTS_REGISTERED");
        }
        if(event.equals(PredefinedWishEventRegistry.ENDLESS_NIGHT)){long expires=c.level().getGameTime()+intensity*1200L;c.execution().leaseEvent(event,expires);long day=c.level().getDayTime()/24000L*24000L;c.level().setDayTime(day+18000);return WishActionResult.success(1);}
        if(event.equals(PredefinedWishEventRegistry.OMINOUS_STORM)){c.level().setWeatherParameters(0,intensity*1200,true,true);c.level().playSound(null,p.getX(),p.getY(),p.getZ(),SoundEvents.AMBIENT_CAVE.get(),SoundSource.AMBIENT,1,0.7f);int spawned=0;for(int n=0;n<Math.min(4,intensity);n++){LightningBolt bolt=EntityType.LIGHTNING_BOLT.create(c.level());if(bolt==null)continue;Vec3 pos=SafeSpawnPositionFinder.find(c.level(),bolt,p.position(),16,48,false,p.getYRot(),c.execution().executionId().getLeastSignificantBits()+n);if(pos!=null){bolt.moveTo(pos);if(c.level().addFreshEntity(bolt))spawned++;}}return WishActionResult.success(1+spawned);}
        c.level().playSound(null,p.getX(),p.getY(),p.getZ(),SoundEvents.AMBIENT_CAVE.get(),SoundSource.AMBIENT,1,0.6f);c.execution().leaseEvent(PredefinedWishEventRegistry.stalkerLease(c.stepIndex()),c.level().getGameTime()+60);return WishActionResult.success(1);
    });}
}
