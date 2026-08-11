package com.ikunkk02.wishingwillow.execution;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Persisted per-step progress for a bounded, resumable physical block shower. */
public final class FallingBlockShowerProgress {
    private int spawned;
    private int delivered;
    private int failed;
    private final Map<UUID, BlockPos> active = new LinkedHashMap<>();

    public int spawned() { return spawned; }
    public int delivered() { return delivered; }
    public int failed() { return failed; }
    public int activeCount() { return active.size(); }
    public Map<UUID, BlockPos> active() { return Map.copyOf(active); }

    public void track(UUID entity, BlockPos position) {
        if (active.putIfAbsent(entity, position.immutable()) == null) spawned++;
    }

    public void update(UUID entity, BlockPos position) {
        if (active.containsKey(entity)) active.put(entity, position.immutable());
    }

    public void settle(UUID entity, boolean success) {
        if (active.remove(entity) == null) return;
        if (success) delivered++; else failed++;
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Spawned", spawned); tag.putInt("Delivered", delivered); tag.putInt("Failed", failed);
        ListTag entities = new ListTag();
        active.forEach((id, pos) -> {
            CompoundTag value = new CompoundTag(); value.putUUID("Entity", id);
            value.putInt("X", pos.getX()); value.putInt("Y", pos.getY()); value.putInt("Z", pos.getZ());
            entities.add(value);
        });
        tag.put("Active", entities);
        return tag;
    }

    static FallingBlockShowerProgress load(CompoundTag tag) {
        FallingBlockShowerProgress progress = new FallingBlockShowerProgress();
        progress.spawned = Math.max(0, tag.getInt("Spawned"));
        progress.delivered = Math.max(0, tag.getInt("Delivered"));
        progress.failed = Math.max(0, tag.getInt("Failed"));
        for (Tag value : tag.getList("Active", Tag.TAG_COMPOUND)) {
            CompoundTag entity = (CompoundTag) value;
            if (entity.hasUUID("Entity")) progress.active.put(entity.getUUID("Entity"),
                    new BlockPos(entity.getInt("X"), entity.getInt("Y"), entity.getInt("Z")));
        }
        return progress;
    }
}
