package com.ikunkk02.wishingwillow.execution;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.*;

public final class WishExecutionSavedData extends SavedData {
    public static final String DATA_NAME="wishing_willow_executions";
    private final Map<UUID,WishExecutionRecord> byExecution=new HashMap<>();
    private final Map<UUID,UUID> executionByPlan=new HashMap<>();
    private final Map<UUID,UUID> executionBySession=new HashMap<>();

    public static WishExecutionSavedData get(MinecraftServer server){return server.overworld().getDataStorage().computeIfAbsent(WishExecutionSavedData::load,WishExecutionSavedData::new,DATA_NAME);}
    public static WishExecutionSavedData load(CompoundTag tag){WishExecutionSavedData data=new WishExecutionSavedData();for(Tag value:tag.getList("Executions",Tag.TAG_COMPOUND)){WishExecutionRecord record=WishExecutionRecord.load((CompoundTag)value);data.index(record);}return data;}
    public boolean add(WishExecutionRecord record){if(executionByPlan.containsKey(record.planId())||executionBySession.containsKey(record.wishSessionId()))return false;index(record);setDirty();return true;}
    public void changed(){setDirty();}
    @Nullable public WishExecutionRecord get(UUID id){return byExecution.get(id);}
    @Nullable public WishExecutionRecord byPlan(UUID id){UUID execution=executionByPlan.get(id);return execution==null?null:byExecution.get(execution);}
    @Nullable public WishExecutionRecord bySession(UUID id){UUID execution=executionBySession.get(id);return execution==null?null:byExecution.get(execution);}
    public Collection<WishExecutionRecord> all(){return List.copyOf(byExecution.values());}
    private void index(WishExecutionRecord record){byExecution.put(record.executionId(),record);executionByPlan.put(record.planId(),record.executionId());executionBySession.put(record.wishSessionId(),record.executionId());}
    @Override public CompoundTag save(CompoundTag tag){ListTag list=new ListTag();byExecution.values().stream().sorted(Comparator.comparing(WishExecutionRecord::executionId)).map(WishExecutionRecord::save).forEach(list::add);tag.put("Executions",list);return tag;}
}
