package com.ikunkk02.wishingwillow.execution;

import com.google.gson.JsonObject;
import com.ikunkk02.wishingwillow.WishingWillow;
import com.ikunkk02.wishingwillow.ai.WishCapability;
import com.ikunkk02.wishingwillow.planning.*;
import com.ikunkk02.wishingwillow.research.RegistryEntryType;
import com.ikunkk02.wishingwillow.research.VerifiedRegistryResource;
import com.ikunkk02.wishingwillow.execution.action.WishActionRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.ModList;

import java.util.Set;

public final class WishExecutionValidator {
    private WishExecutionValidator(){}
    public static void validate(MinecraftServer server,WishPlan plan,WishActionRegistry registry){
        if(plan==null||plan.schemaVersion()!=1||plan.steps().isEmpty()||plan.steps().size()>WishPlanBudget.maxSteps(plan.severity()))fail("INVALID_PLAN");
        int destructive=0;
        for(int i=0;i<plan.steps().size();i++){
            WishPlanStep step=plan.steps().get(i);validateStep(server,plan,i,registry);destructive+=WishPlanBudget.destructiveCost(step);
        }
        if(destructive>WishPlanBudget.maxDestructiveCost(plan.severity()))fail("BUDGET_EXCEEDED");
    }
    static void validateStep(MinecraftServer server,WishPlan plan,int index,WishActionRegistry registry){WishPlanStep step=plan.steps().get(index);if(step.stepIndex()!=index)fail("INVALID_STEP_INDEX");validateTiming(step);validateReference(server,step);validateParameters(step,plan.severity());validateSettings(step,plan.severity());if(step.candidateReference().riskScore()>=85&&plan.severity()<81)fail("BUDGET_EXCEEDED");if(!registry.contains(step.action()))fail("UNSUPPORTED_ACTION");}
    private static void validateTiming(WishPlanStep s){boolean valid=switch(s.timing()){
        case IMMEDIATE->s.delaySeconds()==0&&s.trigger()==WishTriggerType.NONE;
        case DELAYED->s.delaySeconds()>=1&&s.delaySeconds()<=86400&&s.trigger()==WishTriggerType.AFTER_DELAY;
        case TRIGGERED->s.delaySeconds()==0&&s.trigger()!=WishTriggerType.NONE&&s.trigger()!=WishTriggerType.AFTER_DELAY;
        case DELAYED_AFTER_TRIGGER->s.delaySeconds()>=1&&s.delaySeconds()<=86400&&s.trigger()!=WishTriggerType.NONE&&s.trigger()!=WishTriggerType.AFTER_DELAY;};if(!valid)fail("INVALID_TIMING");}
    private static void validateReference(MinecraftServer server,WishPlanStep step){CandidateReference r=step.candidateReference();if(r==null||!r.candidateId().equals(step.candidateId())||r.requestedCapability()!=step.capability())fail("INVALID_CANDIDATE");
        VerifiedRegistryResource resource=r.registryResource();RegistryEntryType expected=expected(step.action());
        if(expected!=null&&(resource==null||resource.type()!=expected))fail("INVALID_RESOURCE_TYPE");
        if(step.action()==WishActionType.TELEPORT&&step.parameters().has("mode")&&step.parameters().get("mode").getAsString().equals("CANDIDATE_DIMENSION")&&(resource==null||resource.type()!=RegistryEntryType.DIMENSION))fail("INVALID_RESOURCE_TYPE");
        if(step.action()==WishActionType.TELEPORT&&step.parameters().has("mode")&&!step.parameters().get("mode").getAsString().equals("CANDIDATE_DIMENSION")&&resource!=null)fail("UNTRUSTED_REGISTRY_CANDIDATE");
        if(expected==null&&step.action()!=WishActionType.TELEPORT&&step.action()!=WishActionType.START_PREDEFINED_EVENT&&resource!=null)fail("UNTRUSTED_REGISTRY_CANDIDATE");
        if(step.action()==WishActionType.START_PREDEFINED_EVENT&&resource!=null)fail("INVALID_EVENT");
        if(resource!=null){ResourceLocation id=ResourceLocation.tryParse(resource.id());if(id==null||!new ServerPlanningEnvironment(server).contains(resource.type(),resource.id()))fail("STALE_RESOURCE");
            if(!id.getNamespace().equals("minecraft")&&!id.getNamespace().equals(WishingWillow.MOD_ID)&&!ModList.get().isLoaded(id.getNamespace()))fail("INVALID_MOD_NAMESPACE");}
        else if(step.action()==WishActionType.START_PREDEFINED_EVENT){if(!r.sourceModId().equals(WishingWillow.MOD_ID)||!PredefinedWishEventRegistry.contains(r.featureName()))fail("INVALID_EVENT");}
        else if(r.sourceKind()!=CandidateSourceKind.VANILLA_BUILTIN)fail("UNTRUSTED_NON_REGISTRY_CANDIDATE");
        if(!supports(step.capability(),r.providedCapability(),step.action()))fail("INVALID_ACTION_CAPABILITY");
    }
    private static boolean supports(WishCapability requested,WishCapability provided,WishActionType action){
        if(new CapabilityRelationGraph().relation(requested,provided)==MatchType.UNSATISFIED)return false;
        return switch(action){
            case GIVE_ITEM->Set.of(WishCapability.GIVE_ITEM,WishCapability.STRONG_WEAPON,WishCapability.INVENTORY_CHANGE).contains(provided);
            case REMOVE_ITEM->Set.of(WishCapability.REMOVE_ITEM,WishCapability.INVENTORY_CHANGE).contains(provided);
            case SPAWN_ENTITY,DESPAWN_ENTITY->Set.of(WishCapability.SPAWN_ENTITY,WishCapability.HOSTILE_ENTITY,WishCapability.FRIENDLY_ENTITY,WishCapability.STALKING_ENTITY,WishCapability.PERSISTENT_FOLLOWER,WishCapability.MIMIC_ENTITY,WishCapability.POWERFUL_ENEMY,WishCapability.ENTITY_RECREATION).contains(provided);
            case APPLY_EFFECT,REMOVE_EFFECT,MODIFY_HEALTH->Set.of(WishCapability.POWER_BUFF,WishCapability.POWER_DEBUFF,WishCapability.HEALING,WishCapability.DAMAGE,WishCapability.DARKNESS,WishCapability.IMMORTALITY).contains(provided);
            case TELEPORT->Set.of(WishCapability.TELEPORT,WishCapability.DIMENSION_TRAVEL,WishCapability.SPACE_TRAVEL,WishCapability.SPACECRAFT).contains(provided);
            case CHANGE_TIME->provided==WishCapability.CHANGE_TIME||provided==WishCapability.WORLD_EVENT;
            case CHANGE_WEATHER,LIGHTNING->provided==WishCapability.CHANGE_WEATHER||provided==WishCapability.LIGHTNING||provided==WishCapability.WORLD_EVENT;
            case PLAY_SOUND->provided==WishCapability.SOUND_EVENT;case SPAWN_PARTICLE->provided==WishCapability.VISUAL_EVENT||provided==WishCapability.HALLUCINATION;
            case EXPLOSION->provided==WishCapability.EXPLOSION;case CHANGE_BLOCK,REPLACE_BLOCK_AREA->provided==WishCapability.BLOCK_CHANGE||provided==WishCapability.STRUCTURE;
            case MODIFY_HUNGER,MODIFY_ATTRIBUTE->Set.of(WishCapability.PLAYER_ATTRIBUTE,WishCapability.POWER_BUFF,WishCapability.POWER_DEBUFF).contains(provided);
            case CHANGE_MOB_TARGET,FOLLOW_PLAYER,AVOID_PLAYER->Set.of(WishCapability.MOB_BEHAVIOR,WishCapability.STALKING_ENTITY,WishCapability.PERSISTENT_FOLLOWER,WishCapability.FRIENDLY_ENTITY,WishCapability.HOSTILE_ENTITY).contains(provided);
            case CHANGE_REPUTATION->provided==WishCapability.REPUTATION;case START_PREDEFINED_EVENT->provided==WishCapability.WORLD_EVENT||provided==WishCapability.MEMORY_RELATED_EVENT;};}
    private static RegistryEntryType expected(WishActionType a){return switch(a){case GIVE_ITEM,REMOVE_ITEM->RegistryEntryType.ITEM;case SPAWN_ENTITY,DESPAWN_ENTITY,CHANGE_MOB_TARGET,FOLLOW_PLAYER,AVOID_PLAYER->RegistryEntryType.ENTITY;case APPLY_EFFECT,REMOVE_EFFECT->RegistryEntryType.EFFECT;case PLAY_SOUND->RegistryEntryType.SOUND;case SPAWN_PARTICLE->RegistryEntryType.PARTICLE;case CHANGE_BLOCK,REPLACE_BLOCK_AREA->RegistryEntryType.BLOCK;default->null;};}
    private static void validateParameters(WishPlanStep s,int severity){JsonObject p=s.parameters();try{switch(s.action()){
        case GIVE_ITEM,REMOVE_ITEM->range(p,"count",1,64);case SPAWN_ENTITY->{range(p,"count",1,10);distance(p,128);}case DESPAWN_ENTITY->{range(p,"radius",2,64);range(p,"max_count",1,32);}
        case APPLY_EFFECT->{range(p,"duration_seconds",1,3600);range(p,"amplifier",0,10);}case REMOVE_EFFECT->{}case TELEPORT->{String m=p.get("mode").getAsString();if(!Set.of("NEARBY_SAFE","RANDOM_SAFE","CANDIDATE_DIMENSION").contains(m))fail("INVALID_MODE");if(!m.equals("CANDIDATE_DIMENSION"))distance(p,4096);}
        case CHANGE_TIME->{if(!Set.of("DAY","NIGHT","DAWN","DUSK").contains(p.get("value").getAsString()))fail("INVALID_TIME");}case CHANGE_WEATHER->{if(!Set.of("CLEAR","RAIN","THUNDER").contains(p.get("weather").getAsString()))fail("INVALID_WEATHER");range(p,"duration_seconds",30,3600);}
        case PLAY_SOUND->{decimal(p,"volume",.1,4);decimal(p,"pitch",.5,2);range(p,"distance",2,128);}case SPAWN_PARTICLE->{range(p,"count",1,500);decimal(p,"radius",0,32);}case LIGHTNING->{range(p,"count",1,4);distance(p,64);}
        case EXPLOSION->{double power=p.get("power").getAsDouble();decimal(p,"power",.1,8);distance(p,128);boolean destroys=p.get("destroy_blocks").getAsBoolean();if(severity<41||destroys&&severity<61||power>4&&severity<81)fail("BUDGET_EXCEEDED");}case CHANGE_BLOCK->distance(p,64);case REPLACE_BLOCK_AREA->{range(p,"radius",1,16);range(p,"max_blocks",1,2048);if(severity<41)fail("BUDGET_EXCEEDED");}
        case MODIFY_HEALTH->{decimal(p,"delta",-40,40);if(p.get("allow_lethal").getAsBoolean()&&severity<81)fail("BUDGET_EXCEEDED");}case MODIFY_HUNGER->range(p,"delta",-20,20);case MODIFY_ATTRIBUTE->{if(!Set.of("MAX_HEALTH","MOVEMENT_SPEED","ATTACK_DAMAGE","ARMOR","KNOCKBACK_RESISTANCE","LUCK").contains(p.get("attribute").getAsString()))fail("INVALID_ATTRIBUTE");decimal(p,"amount",-20,20);range(p,"duration_seconds",1,3600);}
        case CHANGE_MOB_TARGET->{range(p,"radius",2,64);range(p,"max_entities",1,32);}case FOLLOW_PLAYER,AVOID_PLAYER->{range(p,"radius",2,64);range(p,"max_entities",1,16);range(p,"duration_seconds",1,3600);}case CHANGE_REPUTATION->{range(p,"delta",-100,100);range(p,"radius",2,64);}case START_PREDEFINED_EVENT->range(p,"intensity",1,5);}}catch(NullPointerException|ClassCastException|NumberFormatException e){fail("INVALID_PARAMETER");}}
    private static void distance(JsonObject p,int max){range(p,"distance_min",2,max);range(p,"distance_max",2,max);if(p.get("distance_min").getAsInt()>p.get("distance_max").getAsInt())fail("INVALID_DISTANCE");}
    private static void validateSettings(WishPlanStep step,int severity){boolean destructive=step.action()==WishActionType.CHANGE_BLOCK||step.action()==WishActionType.REPLACE_BLOCK_AREA||(step.action()==WishActionType.EXPLOSION&&step.parameters().has("destroy_blocks")&&step.parameters().get("destroy_blocks").getAsBoolean());if(destructive&&severity>WishExecutionConfig.MAX_DESTRUCTIVE_SEVERITY.get())fail("DESTRUCTIVE_SEVERITY_DISABLED");if((step.action()==WishActionType.CHANGE_BLOCK||step.action()==WishActionType.REPLACE_BLOCK_AREA)&&(!WishExecutionConfig.BLOCK_MODIFICATION.get()||WishExecutionConfig.DEBUG_SAFE_MODE.get()))fail("BLOCK_MODIFICATION_DISABLED");if(step.action()==WishActionType.EXPLOSION){if(!WishExecutionConfig.EXPLOSIONS.get())fail("EXPLOSIONS_DISABLED");double power=step.parameters().get("power").getAsDouble();boolean destroys=step.parameters().get("destroy_blocks").getAsBoolean();if(destroys&&!WishExecutionConfig.DESTRUCTIVE_EXPLOSIONS.get())fail("DESTRUCTIVE_EXPLOSIONS_DISABLED");if(WishExecutionConfig.DEBUG_SAFE_MODE.get()&&(destroys||power>2))fail("DEBUG_SAFE_MODE");}if(step.action()==WishActionType.TELEPORT&&step.parameters().get("mode").getAsString().equals("CANDIDATE_DIMENSION")&&!WishExecutionConfig.CROSS_DIMENSION_TELEPORT.get())fail("CROSS_DIMENSION_DISABLED");if(step.action()==WishActionType.SPAWN_ENTITY&&step.candidateReference().registryResource()!=null&&!step.candidateReference().registryResource().id().startsWith("minecraft:")){if(!WishExecutionConfig.THIRD_PARTY_ENTITIES.get())fail("THIRD_PARTY_ENTITIES_DISABLED");if(severity<61)fail("THIRD_PARTY_ENTITY_SEVERITY");}}
    private static void range(JsonObject p,String key,int min,int max){int v=p.get(key).getAsInt();if(v<min||v>max)fail("INVALID_PARAMETER");}
    private static void decimal(JsonObject p,String key,double min,double max){double v=p.get(key).getAsDouble();if(!Double.isFinite(v)||v<min||v>max)fail("INVALID_PARAMETER");}
    private static void fail(String code){throw new IllegalArgumentException(code);}
}
