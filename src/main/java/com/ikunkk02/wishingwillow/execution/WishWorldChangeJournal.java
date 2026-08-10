package com.ikunkk02.wishingwillow.execution;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;

public final class WishWorldChangeJournal {
    public record Entry(BlockPos position,CompoundTag oldState,CompoundTag newState){}
    private final List<Entry> entries=new ArrayList<>();private int cursor;
    public boolean prepared(){return !entries.isEmpty();}public int cursor(){return cursor;}public int size(){return entries.size();}public boolean complete(){return cursor>=entries.size();}
    public void add(BlockPos pos,BlockState oldState,BlockState newState){entries.add(new Entry(pos.immutable(),NbtUtils.writeBlockState(oldState),NbtUtils.writeBlockState(newState)));}
    public List<Entry> next(int limit){return List.copyOf(entries.subList(cursor,Math.min(entries.size(),cursor+limit)));}
    public void advance(int count){cursor=Math.min(entries.size(),cursor+Math.max(0,count));}
    public CompoundTag save(){CompoundTag tag=new CompoundTag();tag.putInt("Cursor",cursor);ListTag list=new ListTag();for(Entry e:entries){CompoundTag v=new CompoundTag();v.putLong("Pos",e.position.asLong());v.put("Old",e.oldState.copy());v.put("New",e.newState.copy());list.add(v);}tag.put("Entries",list);return tag;}
    public static WishWorldChangeJournal load(CompoundTag tag){WishWorldChangeJournal journal=new WishWorldChangeJournal();for(Tag value:tag.getList("Entries",Tag.TAG_COMPOUND)){CompoundTag v=(CompoundTag)value;journal.entries.add(new Entry(BlockPos.of(v.getLong("Pos")),v.getCompound("Old").copy(),v.getCompound("New").copy()));}journal.cursor=Math.max(0,Math.min(journal.entries.size(),tag.getInt("Cursor")));return journal;}
}
