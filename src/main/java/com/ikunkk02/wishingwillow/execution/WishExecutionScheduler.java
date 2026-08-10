package com.ikunkk02.wishingwillow.execution;

import com.ikunkk02.wishingwillow.planning.WishTriggerType;
import java.util.*;

public final class WishExecutionScheduler {
    public record StepKey(UUID executionId,int stepIndex) { }
    private record Scheduled(long time,StepKey key) implements Comparable<Scheduled>{public int compareTo(Scheduled other){int c=Long.compare(time,other.time);return c!=0?c:key.toString().compareTo(other.key.toString());}}
    private final PriorityQueue<Scheduled> delayed=new PriorityQueue<>();
    private final EnumMap<WishTriggerType,Set<StepKey>> triggers=new EnumMap<>(WishTriggerType.class);
    private final Set<StepKey> indexed=new HashSet<>();
    public WishExecutionScheduler(){for(WishTriggerType type:WishTriggerType.values())triggers.put(type,new LinkedHashSet<>());}
    public void clear(){delayed.clear();indexed.clear();triggers.values().forEach(Set::clear);}
    public void delay(StepKey key,long time){if(indexed.add(key))delayed.add(new Scheduled(time,key));}
    public void trigger(StepKey key,WishTriggerType type){if(indexed.add(key))triggers.get(type).add(key);}
    public void remove(StepKey key){indexed.remove(key);triggers.values().forEach(set->set.remove(key));}
    public List<StepKey> due(long now,int budget){List<StepKey> result=new ArrayList<>();while(result.size()<budget&&!delayed.isEmpty()&&delayed.peek().time<=now){Scheduled value=delayed.poll();if(indexed.remove(value.key))result.add(value.key);}return result;}
    public List<StepKey> fire(WishTriggerType type,java.util.function.Predicate<StepKey> filter){List<StepKey> result=triggers.get(type).stream().filter(filter).toList();result.forEach(this::remove);return result;}
    public Set<StepKey> waiting(WishTriggerType type){return Set.copyOf(triggers.get(type));}
    public int scheduledCount(){return indexed.size();}
}
