package com.ikunkk02.wishingwillow.execution;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Persisted per-step progress for bounded, resumable ItemEntity rain. */
public final class ItemRainProgress {
    public static final String ENTITY_SESSION_TAG = "WishingWillowItemRainSession";
    private int spawnedUnits;
    private int spawnedEntities;
    private int initialInventoryUnits = -1;
    private long allSpawnedGameTime = -1;
    private int deliveredUnits;
    private int failedUnits;
    private final Map<UUID, Integer> active = new LinkedHashMap<>();

    public int spawnedUnits() { return spawnedUnits; }
    public int spawnedEntities() { return spawnedEntities; }
    public int initialInventoryUnits() { return initialInventoryUnits; }
    public long allSpawnedGameTime() { return allSpawnedGameTime; }
    public int deliveredUnits() { return deliveredUnits; }
    public int failedUnits() { return failedUnits; }
    public Map<UUID, Integer> active() { return Map.copyOf(active); }

    public boolean start(int inventoryUnits) {
        if (initialInventoryUnits >= 0) return false;
        initialInventoryUnits = Math.max(0, inventoryUnits);
        return true;
    }

    public void track(UUID entity, int units) {
        int safeUnits = Math.max(0, units);
        if (safeUnits == 0 || active.putIfAbsent(entity, safeUnits) != null) return;
        spawnedUnits += safeUnits;
        spawnedEntities++;
    }

    /** Tracks a guaranteed-delivery replacement without counting it as newly requested units. */
    public void trackReplacement(UUID entity, int units) {
        int safeUnits = Math.max(0, units);
        if (safeUnits > 0) active.put(entity, safeUnits);
    }

    public void allSpawned(long gameTime) {
        if (allSpawnedGameTime < 0) allSpawnedGameTime = gameTime;
    }

    public void complete(int delivered, int failed) {
        deliveredUnits = Math.max(0, delivered);
        failedUnits = Math.max(0, failed);
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("SpawnedUnits", spawnedUnits);
        tag.putInt("SpawnedEntities", spawnedEntities);
        tag.putInt("InitialInventoryUnits", initialInventoryUnits);
        tag.putLong("AllSpawnedGameTime", allSpawnedGameTime);
        tag.putInt("DeliveredUnits", deliveredUnits);
        tag.putInt("FailedUnits", failedUnits);
        ListTag entities = new ListTag();
        active.forEach((id, units) -> {
            CompoundTag value = new CompoundTag();
            value.putUUID("Entity", id);
            value.putInt("Units", units);
            entities.add(value);
        });
        tag.put("Active", entities);
        return tag;
    }

    static ItemRainProgress load(CompoundTag tag) {
        ItemRainProgress progress = new ItemRainProgress();
        progress.spawnedUnits = Math.max(0, tag.getInt("SpawnedUnits"));
        progress.spawnedEntities = Math.max(0, tag.getInt("SpawnedEntities"));
        progress.initialInventoryUnits = tag.contains("InitialInventoryUnits")
                ? tag.getInt("InitialInventoryUnits") : -1;
        progress.allSpawnedGameTime = tag.contains("AllSpawnedGameTime")
                ? tag.getLong("AllSpawnedGameTime") : -1;
        progress.deliveredUnits = Math.max(0, tag.getInt("DeliveredUnits"));
        progress.failedUnits = Math.max(0, tag.getInt("FailedUnits"));
        for (Tag value : tag.getList("Active", Tag.TAG_COMPOUND)) {
            CompoundTag entity = (CompoundTag) value;
            if (entity.hasUUID("Entity")) {
                progress.active.put(entity.getUUID("Entity"), Math.max(0, entity.getInt("Units")));
            }
        }
        return progress;
    }
}
