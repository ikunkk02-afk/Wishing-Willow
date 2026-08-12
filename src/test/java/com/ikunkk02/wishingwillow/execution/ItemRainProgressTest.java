package com.ikunkk02.wishingwillow.execution;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ItemRainProgressTest {
    @Test
    void itemUnitsAndEntityCountsPersistWithoutMultiplication() {
        ItemRainProgress progress = new ItemRainProgress();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID replacement = UUID.randomUUID();

        assertTrue(progress.start(12));
        assertFalse(progress.start(99));
        progress.track(first, 4);
        progress.track(second, 8);
        progress.track(first, 4);
        progress.trackReplacement(replacement, 12);
        progress.allSpawned(120);
        progress.complete(12, 0);

        ItemRainProgress loaded = ItemRainProgress.load(progress.save());
        assertEquals(12, loaded.spawnedUnits());
        assertEquals(2, loaded.spawnedEntities());
        assertEquals(12, loaded.initialInventoryUnits());
        assertEquals(120, loaded.allSpawnedGameTime());
        assertEquals(12, loaded.deliveredUnits());
        assertEquals(0, loaded.failedUnits());
        assertEquals(4, loaded.active().get(first));
        assertEquals(8, loaded.active().get(second));
        assertEquals(12, loaded.active().get(replacement));
    }

    @Test
    void executionRecordPersistsItemRainForCrashRecovery() {
        UUID execution = UUID.randomUUID();
        WishExecutionRecord record = new WishExecutionRecord(execution, execution, UUID.randomUUID(),
                UUID.randomUUID(), 1, 10, ExecutionSource.WISH_PROGRAM, 1);
        record.itemRain(0).start(0);
        record.itemRain(0).track(UUID.randomUUID(), 64);

        WishExecutionRecord loaded = WishExecutionRecord.load(record.save());
        assertEquals(64, loaded.itemRain(0).spawnedUnits());
        assertEquals(1, loaded.itemRain(0).spawnedEntities());
        assertEquals(ExecutionSource.WISH_PROGRAM, loaded.source());
    }
}
