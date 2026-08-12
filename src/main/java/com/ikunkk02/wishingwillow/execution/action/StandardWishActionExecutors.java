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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
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
    private static WishActionExecutor executor(Action action){return new WishActionExecutor(){public WishActionResult validate(WishExecutionContext c){return basic(c);}public WishActionResult execute(WishExecutionContext c){try{return action.run(c);}catch(Throwable error){WishingWillow.LOGGER.error("Wish action isolated: execution={} step={} action={}",c.execution().executionId(),c.stepIndex(),c.actionId(),error);return WishActionResult.failed("ACTION_EXCEPTION_"+error.getClass().getSimpleName());}}};}
    private static WishActionResult basic(WishExecutionContext c){if(c.player()==null&&c.target()!=WishTargetType.WORLD)return WishActionResult.retry("WAITING_TARGET");return WishActionResult.success(0);}
    private static ResourceLocation id(WishExecutionContext c){VerifiedRegistryResource r=c.candidate()==null?null:c.candidate().registryResource();return r==null?null:ResourceLocation.tryParse(r.id());}
    private static int i(JsonObject p,String k){return p.get(k).getAsInt();}private static double d(JsonObject p,String k){return p.get(k).getAsDouble();}private static boolean b(JsonObject p,String k){return p.get(k).getAsBoolean();}

    static WishActionExecutor giveItem(){return executor(c->{
        ServerPlayer player=c.player();Item item=ForgeRegistries.ITEMS.getValue(id(c));
        if(item==null)return WishActionResult.stale("ITEM_NOT_FOUND");
        JsonObject parameters=c.parameters();int left=i(parameters,"count"),given=0;
        WishingWillow.LOGGER.info("Advanced item build started item={} advanced={} requested={}",id(c),advanced(parameters),left);
        while(left>0){
            int amount=Math.min(left,item.getMaxStackSize());ItemStack stack=new ItemStack(item,amount);
            WishActionResult built=buildAdvancedItem(stack,parameters,id(c));if(!built.successful())return built;
            player.getInventory().add(stack);int accepted=amount-stack.getCount();given+=accepted;left-=amount;
            if(!stack.isEmpty()){player.drop(stack,false);given+=stack.getCount();}
        }
        WishingWillow.LOGGER.info("Advanced item completed item={} enchantments={} given={}",id(c),
                parameters.has("enchantments")?parameters.getAsJsonArray("enchantments").size():0,given);
        return given==i(parameters,"count")?WishActionResult.success(given):WishActionResult.partial("ITEM_DELIVERY_PARTIAL",given);
    });}

    private static boolean advanced(JsonObject p){return p.has("enchantments")||p.has("custom_name")||p.has("damage")
            ||p.has("unbreakable")||p.has("attributes")||p.has("custom_data");}

    private static WishActionResult buildAdvancedItem(ItemStack stack,JsonObject p,ResourceLocation itemId){
        Map<Enchantment,Integer> enchantments=new LinkedHashMap<>();
        if(p.has("enchantments"))for(var element:p.getAsJsonArray("enchantments")){
            JsonObject requested=element.getAsJsonObject();ResourceLocation enchantmentId=ResourceLocation.tryParse(requested.get("id").getAsString());
            Enchantment enchantment=ForgeRegistries.ENCHANTMENTS.getValue(enchantmentId);if(enchantment==null)return WishActionResult.stale("INVALID_ENCHANTMENT_RESOURCE");
            int level=requested.get("level").getAsInt();enchantments.put(enchantment,level);
            WishingWillow.LOGGER.info("Item enchantment resolved enchantment={} level={}",enchantmentId,level);
        }
        if(!enchantments.isEmpty()){
            EnchantmentHelper.setEnchantments(enchantments,stack);
            for(var entry:enchantments.entrySet()){
                ResourceLocation enchantmentId=ForgeRegistries.ENCHANTMENTS.getKey(entry.getKey());
                if(EnchantmentHelper.getItemEnchantmentLevel(entry.getKey(),stack)!=entry.getValue())return WishActionResult.failed("ITEM_ENCHANTMENT_VERIFICATION_FAILED");
                WishingWillow.LOGGER.info("Item enchantment applied enchantment={} level={}",enchantmentId,entry.getValue());
            }
        }
        if(p.has("custom_name"))stack.setHoverName(Component.literal(p.get("custom_name").getAsString()));
        if(p.has("damage")){if(!stack.isDamageableItem())return WishActionResult.failed("ITEM_NOT_DAMAGEABLE");stack.setDamageValue(Math.min(p.get("damage").getAsInt(),stack.getMaxDamage()));}
        if(p.has("unbreakable")&&p.get("unbreakable").getAsBoolean())stack.getOrCreateTag().putBoolean("Unbreakable",true);
        if(p.has("attributes")){
            int index=0;for(var element:p.getAsJsonArray("attributes")){
                JsonObject attributeJson=element.getAsJsonObject();ResourceLocation attributeId=ResourceLocation.tryParse(attributeJson.get("id").getAsString());
                Attribute attribute=ForgeRegistries.ATTRIBUTES.getValue(attributeId);if(attribute==null)return WishActionResult.stale("INVALID_ATTRIBUTE_RESOURCE");
                AttributeModifier.Operation operation=switch(attributeJson.get("operation").getAsString()){
                    case "multiply_base"->AttributeModifier.Operation.MULTIPLY_BASE;case "multiply_total"->AttributeModifier.Operation.MULTIPLY_TOTAL;default->AttributeModifier.Operation.ADDITION;};
                EquipmentSlot slot=switch(attributeJson.get("slot").getAsString()){
                    case "offhand"->EquipmentSlot.OFFHAND;case "head"->EquipmentSlot.HEAD;case "chest"->EquipmentSlot.CHEST;
                    case "legs"->EquipmentSlot.LEGS;case "feet"->EquipmentSlot.FEET;default->EquipmentSlot.MAINHAND;};
                UUID uuid=UUID.nameUUIDFromBytes((itemId+"|"+attributeId+"|"+slot+"|"+index++).getBytes(StandardCharsets.UTF_8));
                stack.addAttributeModifier(attribute,new AttributeModifier(uuid,"Wishing Willow advanced item",attributeJson.get("amount").getAsDouble(),operation),slot);
            }
        }
        if(p.has("custom_data"))try{
            CompoundTag custom=TagParser.parseTag(p.get("custom_data").toString());stack.getOrCreateTag().merge(custom);
        }catch(com.mojang.brigadier.exceptions.CommandSyntaxException error){return WishActionResult.failed("INVALID_CUSTOM_DATA");}
        if(stack.isEmpty()||ForgeRegistries.ITEMS.getKey(stack.getItem())==null||!ForgeRegistries.ITEMS.getKey(stack.getItem()).equals(itemId))return WishActionResult.failed("ITEM_STACK_VERIFICATION_FAILED");
        return WishActionResult.success(stack.getCount());
    }
    static WishActionExecutor removeItem(){return executor(c->{ServerPlayer p=c.player();Item item=ForgeRegistries.ITEMS.getValue(id(c));if(item==null)return WishActionResult.stale("ITEM_NOT_FOUND");int wanted=i(c.parameters(),"count"),removed=0;for(int slot=0;slot<p.getInventory().getContainerSize()&&removed<wanted;slot++){ItemStack stack=p.getInventory().getItem(slot);if(!stack.is(item))continue;int take=Math.min(stack.getCount(),wanted-removed);stack.shrink(take);removed+=take;}return removed==wanted?WishActionResult.success(removed):WishActionResult.partial("INSUFFICIENT_ITEMS",removed);});}
    static WishActionExecutor spawnEntity(){return executor(c->{ResourceLocation resource=id(c);EntityType<?> type=ForgeRegistries.ENTITY_TYPES.getValue(resource);if(type==null)return WishActionResult.stale("ENTITY_NOT_FOUND");if(!resource.getNamespace().equals("minecraft")&&!WishExecutionConfig.THIRD_PARTY_ENTITIES.get())return WishActionResult.failed("THIRD_PARTY_ENTITIES_DISABLED");ServerPlayer player=c.player();int count=i(c.parameters(),"count"),spawned=0;for(int n=0;n<count;n++){Entity entity;try{entity=type.create(c.level());}catch(Throwable error){return spawned>0?WishActionResult.partial("ENTITY_CREATE_FAILED",spawned):WishActionResult.failed("ENTITY_CREATE_FAILED");}if(entity==null)return spawned>0?WishActionResult.partial("ENTITY_CREATE_NULL",spawned):WishActionResult.failed("ENTITY_CREATE_FAILED");Vec3 pos=SafeSpawnPositionFinder.find(c.level(),entity,player.position(),i(c.parameters(),"distance_min"),i(c.parameters(),"distance_max"),c.capability()==com.ikunkk02.wishingwillow.ai.WishCapability.STALKING_ENTITY,player.getYRot(),c.execution().executionId().getLeastSignificantBits()+c.stepIndex()*31L+n);if(pos==null)continue;entity.moveTo(pos.x,pos.y,pos.z,player.getYRot()+180,0);if(c.capability()==com.ikunkk02.wishingwillow.ai.WishCapability.PERSISTENT_FOLLOWER||c.capability()==com.ikunkk02.wishingwillow.ai.WishCapability.FRIENDLY_ENTITY){if(entity instanceof Mob mob)mob.setPersistenceRequired();if(entity instanceof TamableAnimal tame)tame.tame(player);}try{if(c.level().addFreshEntity(entity)){c.execution().bindEntity(c.stepIndex(),entity.getUUID());spawned++;}}catch(Throwable ignored){entity.discard();}}return spawned==count?WishActionResult.success(spawned):spawned>0?WishActionResult.partial("SAFE_POSITION_OR_SPAWN_FAILED",spawned):WishActionResult.failed("ENTITY_SPAWN_FAILED");});}
    static WishActionExecutor despawnEntity(){return executor(c->{EntityType<?> expected=ForgeRegistries.ENTITY_TYPES.getValue(id(c));int max=i(c.parameters(),"max_count"),removed=0;for(UUID uuid:c.execution().allEntities()){Entity entity=c.level().getEntity(uuid);if(entity!=null&&entity.getType()==expected&&entity.distanceToSqr(c.player())<=Math.pow(i(c.parameters(),"radius"),2)&&removed<max){entity.discard();removed++;}}return WishActionResult.success(removed);});}
    static WishActionExecutor entitySuppression(){return executor(c->{ServerPlayer player=c.player();if(player==null)return WishActionResult.retry("WAITING_TARGET");JsonObject p=c.parameters();var group=WishEntitySuppressionSavedData.Group.valueOf(p.get("group").getAsString().toUpperCase(Locale.ROOT));var scope=WishEntitySuppressionSavedData.Scope.valueOf(p.get("scope").getAsString().toUpperCase(Locale.ROOT));var mode=WishEntitySuppressionSavedData.DisappearanceMode.valueOf(p.get("disappearance_mode").getAsString().toUpperCase(Locale.ROOT));var rule=new WishEntitySuppressionSavedData.Rule(UUID.randomUUID(),player.getUUID(),c.execution().wishSessionId(),group,scope,c.level().dimension().location(),b(p,"remove_existing"),b(p,"prevent_future"),mode,System.currentTimeMillis(),b(p,"permanent"),true,true);WishEntitySuppressionSavedData data=WishEntitySuppressionSavedData.get(player.server);data.add(rule);int removed=rule.removeExisting()?data.applyLoaded(player.server,rule):0;return WishActionResult.success(Math.max(1,removed));});}
    static WishActionExecutor restoreEntitySpawning(){return executor(c->{ServerPlayer player=c.player();if(player==null)return WishActionResult.retry("WAITING_TARGET");JsonObject p=c.parameters();var group=WishEntitySuppressionSavedData.Group.valueOf(p.get("group").getAsString().toUpperCase(Locale.ROOT));var scope=WishEntitySuppressionSavedData.Scope.valueOf(p.get("scope").getAsString().toUpperCase(Locale.ROOT));WishEntitySuppressionSavedData data=WishEntitySuppressionSavedData.get(player.server);int rules=data.removeMatching(player.getUUID(),group,scope,c.level().dimension().location());int wanted=i(p,"initial_count"),radius=i(p,"radius"),spawned=0;List<EntityType<? extends Mob>> types=List.of(EntityType.CHICKEN,EntityType.COW,EntityType.SHEEP,EntityType.PIG,EntityType.VILLAGER);for(int n=0;n<wanted;n++){EntityType<? extends Mob> type=types.get(n%types.size());Mob mob=type.create(c.level());if(mob==null)continue;Vec3 pos=SafeSpawnPositionFinder.find(c.level(),mob,player.position(),4,radius,false,player.getYRot(),c.execution().executionId().getLeastSignificantBits()+n);if(pos==null)continue;mob.moveTo(pos.x,pos.y,pos.z,player.getYRot(),0);mob.setPersistenceRequired();if(c.level().addFreshEntity(mob)){c.execution().bindEntity(c.stepIndex(),mob.getUUID());spawned++;}else mob.discard();}WishingWillow.LOGGER.info("Entity spawning restored owner={} removedRules={} spawned={} requested={}",player.getUUID(),rules,spawned,wanted);return spawned>0?WishActionResult.success(spawned):rules>0?WishActionResult.success(1):WishActionResult.failed("NO_SUPPRESSION_OR_SAFE_SPAWN");});}
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
    private static WishActionExecutor behavior(WishEntityBehaviorManager.Mode mode){return executor(c->{int affected=0,duration=c.parameters().has("duration_seconds")?i(c.parameters(),"duration_seconds"):600,max=c.parameters().has("max_entities")?i(c.parameters(),"max_entities"):8;boolean permanent=c.parameters().has("permanent")&&b(c.parameters(),"permanent");long durationTicks=permanent?Long.MAX_VALUE/2:duration*20L;for(UUID uuid:c.execution().allEntities()){if(affected>=max)break;Entity entity=c.level().getEntity(uuid);if(entity instanceof Mob){double min=c.parameters().has("radius")?i(c.parameters(),"radius"):16;long expires=c.level().getGameTime()+durationTicks;c.execution().leaseBehavior(uuid,mode.name(),1.1,min,expires);WishEntityBehaviorManager.bind(c.execution().executionId(),uuid,c.player().getUUID(),mode,1.1,min,expires);affected++;}}return affected>0?WishActionResult.success(affected):WishActionResult.retry("WAITING_BOUND_ENTITY");});}
    static WishActionExecutor entityAttractionAura(){return executor(c->{ServerPlayer p=c.player();if(p==null)return WishActionResult.retry("WAITING_TARGET");JsonObject params=c.parameters();double radius=params.has("radius")?d(params,"radius"):64;double strength=params.has("strength")?d(params,"strength"):1.0;boolean permanent=params.has("permanent")&&b(params,"permanent");boolean includeHostile=!params.has("include_hostile")||b(params,"include_hostile");boolean includePassive=!params.has("include_passive")||b(params,"include_passive");boolean includeVillagers=!params.has("include_villagers")||b(params,"include_villagers");boolean includeModded=!params.has("include_modded")||b(params,"include_modded");WishAttractionSavedData.AttractionRule rule=new WishAttractionSavedData.AttractionRule(p.getUUID(),c.execution().wishSessionId().toString(),radius,strength,includeHostile,includePassive,includeVillagers,includeModded,permanent,c.level().getGameTime());WishAttractionSavedData data=WishAttractionSavedData.get(p.server);data.addRule(rule);if(permanent&&params.has("never_alone")&&b(params,"never_alone")){NeverAloneSavedData.get(p.server).add(p,c.execution().wishSessionId().toString(),c.level().getGameTime());c.level().playSound(null,p.getX(),p.getY(),p.getZ(),SoundEvents.ENDER_DRAGON_GROWL,SoundSource.AMBIENT,1.0f,1.25f);c.level().sendParticles(net.minecraft.core.particles.ParticleTypes.PORTAL,p.getX(),p.getEyeY(),p.getZ(),160,8,3,8,0.12);}WishingWillow.LOGGER.info("Entity attraction aura created owner={} radius={} permanent={}",p.getUUID(),radius,permanent);return WishActionResult.success(1);});}

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
