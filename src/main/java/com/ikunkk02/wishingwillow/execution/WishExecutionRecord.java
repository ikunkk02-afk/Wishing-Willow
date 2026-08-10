package com.ikunkk02.wishingwillow.execution;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import javax.annotation.Nullable;
import java.util.*;

public final class WishExecutionRecord {
    private final UUID executionId;
    private final UUID planId;
    private final UUID wishSessionId;
    private final UUID ownerId;
    private WishExecutionState state;
    private final List<WishStepExecution> steps;
    private final Map<Integer, List<UUID>> entityBindings;
    private final Map<Integer, String> selectedResources;
    private final Map<Integer, WishWorldChangeJournal> journals;
    private final Map<Integer, AttributeLease> attributeLeases;
    private final Map<UUID, BehaviorLease> behaviorLeases;
    private final Map<String, Long> eventLeases;
    private final long createdGameTime;
    private long updatedGameTime;
    private String lastError;

    public WishExecutionRecord(UUID executionId, UUID planId, UUID wishSessionId, UUID ownerId,
                               int stepCount, long gameTime) {
        this.executionId = executionId; this.planId = planId; this.wishSessionId = wishSessionId;
        this.ownerId = ownerId; this.state = WishExecutionState.VALIDATING;
        this.steps = new ArrayList<>();
        for (int i = 0; i < stepCount; i++) steps.add(new WishStepExecution(i));
        this.entityBindings = new HashMap<>(); this.selectedResources = new HashMap<>();
        this.journals = new HashMap<>(); this.attributeLeases = new HashMap<>();
        this.behaviorLeases = new HashMap<>(); this.eventLeases = new HashMap<>();
        this.createdGameTime = gameTime; this.updatedGameTime = gameTime; this.lastError = "";
    }

    private WishExecutionRecord(UUID executionId, UUID planId, UUID wishSessionId, UUID ownerId,
                                WishExecutionState state, List<WishStepExecution> steps,
                                Map<Integer, List<UUID>> bindings, Map<Integer, String> resources,
                                Map<Integer,WishWorldChangeJournal> journals,Map<Integer,AttributeLease> leases,
                                Map<UUID,BehaviorLease> behaviorLeases,Map<String,Long> eventLeases,
                                long created, long updated, String error) {
        this.executionId=executionId;this.planId=planId;this.wishSessionId=wishSessionId;this.ownerId=ownerId;
        this.state=state;this.steps=steps;this.entityBindings=bindings;this.selectedResources=resources;this.journals=journals;this.attributeLeases=leases;this.behaviorLeases=behaviorLeases;this.eventLeases=eventLeases;
        this.createdGameTime=created;this.updatedGameTime=updated;this.lastError=error;
    }

    public UUID executionId(){return executionId;} public UUID planId(){return planId;}
    public UUID wishSessionId(){return wishSessionId;} public UUID ownerId(){return ownerId;}
    public WishExecutionState state(){return state;} public List<WishStepExecution> steps(){return List.copyOf(steps);}
    public long createdGameTime(){return createdGameTime;} public long updatedGameTime(){return updatedGameTime;}
    public String lastError(){return lastError;}
    public WishStepExecution step(int index){return index>=0&&index<steps.size()?steps.get(index):null;}
    public void transition(WishExecutionState next,long now){if(state.terminal()&&next!=state)throw new IllegalStateException("TERMINAL_EXECUTION");state=next;updatedGameTime=now;}
    public void fail(String error,long now){lastError=error==null?"":error;transition(WishExecutionState.FAILED,now);}
    public void selectResource(int step,String id){if(id!=null&&!id.isBlank())selectedResources.put(step,id);}
    @Nullable public String selectedResource(int step){return selectedResources.get(step);}
    public void bindEntity(int step,UUID entity){entityBindings.computeIfAbsent(step,k->new ArrayList<>()).add(entity);}
    public List<UUID> entitiesForStep(int step){return List.copyOf(entityBindings.getOrDefault(step,List.of()));}
    public List<UUID> allEntities(){return entityBindings.values().stream().flatMap(Collection::stream).distinct().toList();}
    public WishWorldChangeJournal journal(int step){return journals.computeIfAbsent(step,key->new WishWorldChangeJournal());}
    public Map<Integer,WishWorldChangeJournal> journals(){return Map.copyOf(journals);}
    public void leaseAttribute(int step,String attribute,UUID modifier,long expires){attributeLeases.put(step,new AttributeLease(attribute,modifier,expires));}
    public Map<Integer,AttributeLease> attributeLeases(){return Map.copyOf(attributeLeases);}
    public void removeLease(int step){attributeLeases.remove(step);}
    public void leaseBehavior(UUID entity,String mode,double speed,double minDistance,long expires){behaviorLeases.put(entity,new BehaviorLease(entity,mode,speed,minDistance,expires));}
    public Map<UUID,BehaviorLease> behaviorLeases(){return Map.copyOf(behaviorLeases);}
    public void leaseEvent(String id,long expires){eventLeases.put(id,expires);}
    public Map<String,Long> eventLeases(){return Map.copyOf(eventLeases);}
    public void removeEventLease(String id){eventLeases.remove(id);}

    public CompoundTag save(){
        CompoundTag tag=new CompoundTag();tag.putUUID("ExecutionId",executionId);tag.putUUID("PlanId",planId);
        tag.putUUID("WishSessionId",wishSessionId);tag.putUUID("OwnerId",ownerId);tag.putString("State",state.name());
        tag.putLong("CreatedGameTime",createdGameTime);tag.putLong("UpdatedGameTime",updatedGameTime);tag.putString("LastError",lastError);
        ListTag stepTags=new ListTag();steps.stream().map(WishStepExecution::save).forEach(stepTags::add);tag.put("Steps",stepTags);
        ListTag bindings=new ListTag();entityBindings.forEach((index,ids)->ids.forEach(id->{CompoundTag v=new CompoundTag();v.putInt("Step",index);v.putUUID("Entity",id);bindings.add(v);}));tag.put("EntityBindings",bindings);
        ListTag resources=new ListTag();selectedResources.forEach((index,id)->{CompoundTag v=new CompoundTag();v.putInt("Step",index);v.putString("Id",id);resources.add(v);});tag.put("SelectedResources",resources);
        ListTag journalTags=new ListTag();journals.forEach((index,journal)->{CompoundTag v=journal.save();v.putInt("Step",index);journalTags.add(v);});tag.put("Journals",journalTags);
        ListTag leases=new ListTag();attributeLeases.forEach((index,lease)->{CompoundTag v=new CompoundTag();v.putInt("Step",index);v.putString("Attribute",lease.attribute);v.putUUID("Modifier",lease.modifier);v.putLong("Expires",lease.expires);leases.add(v);});tag.put("AttributeLeases",leases);
        ListTag behaviors=new ListTag();behaviorLeases.forEach((id,lease)->{CompoundTag v=new CompoundTag();v.putUUID("Entity",id);v.putString("Mode",lease.mode);v.putDouble("Speed",lease.speed);v.putDouble("MinDistance",lease.minDistance);v.putLong("Expires",lease.expires);behaviors.add(v);});tag.put("BehaviorLeases",behaviors);
        ListTag events=new ListTag();eventLeases.forEach((id,expires)->{CompoundTag v=new CompoundTag();v.putString("Id",id);v.putLong("Expires",expires);events.add(v);});tag.put("EventLeases",events);
        return tag;
    }

    public static WishExecutionRecord load(CompoundTag tag){
        List<WishStepExecution> steps=new ArrayList<>();for(Tag value:tag.getList("Steps",Tag.TAG_COMPOUND))steps.add(WishStepExecution.load((CompoundTag)value));
        Map<Integer,List<UUID>> bindings=new HashMap<>();for(Tag value:tag.getList("EntityBindings",Tag.TAG_COMPOUND)){CompoundTag v=(CompoundTag)value;bindings.computeIfAbsent(v.getInt("Step"),k->new ArrayList<>()).add(v.getUUID("Entity"));}
        Map<Integer,String> resources=new HashMap<>();for(Tag value:tag.getList("SelectedResources",Tag.TAG_COMPOUND)){CompoundTag v=(CompoundTag)value;resources.put(v.getInt("Step"),v.getString("Id"));}
        Map<Integer,WishWorldChangeJournal> journals=new HashMap<>();for(Tag value:tag.getList("Journals",Tag.TAG_COMPOUND)){CompoundTag v=(CompoundTag)value;journals.put(v.getInt("Step"),WishWorldChangeJournal.load(v));}
        Map<Integer,AttributeLease> leases=new HashMap<>();for(Tag value:tag.getList("AttributeLeases",Tag.TAG_COMPOUND)){CompoundTag v=(CompoundTag)value;if(v.hasUUID("Modifier"))leases.put(v.getInt("Step"),new AttributeLease(v.getString("Attribute"),v.getUUID("Modifier"),v.getLong("Expires")));}
        Map<UUID,BehaviorLease> behaviors=new HashMap<>();for(Tag value:tag.getList("BehaviorLeases",Tag.TAG_COMPOUND)){CompoundTag v=(CompoundTag)value;if(v.hasUUID("Entity")){UUID id=v.getUUID("Entity");behaviors.put(id,new BehaviorLease(id,v.getString("Mode"),v.getDouble("Speed"),v.getDouble("MinDistance"),v.getLong("Expires")));}}
        Map<String,Long> events=new HashMap<>();for(Tag value:tag.getList("EventLeases",Tag.TAG_COMPOUND)){CompoundTag v=(CompoundTag)value;events.put(v.getString("Id"),v.getLong("Expires"));}
        WishExecutionState state;try{state=WishExecutionState.valueOf(tag.getString("State"));}catch(IllegalArgumentException ignored){state=WishExecutionState.STALE;}
        boolean hasRunning=steps.stream().anyMatch(step->step.state()==WishStepExecutionState.RUNNING);
        if(state==WishExecutionState.RUNNING){boolean resumable=false;for(WishStepExecution step:steps)if(step.state()==WishStepExecutionState.RUNNING&&journals.containsKey(step.stepIndex())){step.recoverBlockBatch();resumable=true;}if(!resumable)for(WishStepExecution step:steps)step.markStale();state=resumable?WishExecutionState.SCHEDULED:WishExecutionState.STALE;}
        else if(hasRunning){for(WishStepExecution step:steps)step.markStale();if(!state.terminal())state=WishExecutionState.STALE;}
        return new WishExecutionRecord(tag.getUUID("ExecutionId"),tag.getUUID("PlanId"),tag.getUUID("WishSessionId"),tag.getUUID("OwnerId"),state,steps,bindings,resources,journals,leases,behaviors,events,tag.getLong("CreatedGameTime"),tag.getLong("UpdatedGameTime"),tag.getString("LastError"));
    }
    public record AttributeLease(String attribute,UUID modifier,long expires){}
    public record BehaviorLease(UUID entity,String mode,double speed,double minDistance,long expires){}
}
