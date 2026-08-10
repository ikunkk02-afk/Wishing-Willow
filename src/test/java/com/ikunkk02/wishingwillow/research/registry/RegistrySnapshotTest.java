package com.ikunkk02.wishingwillow.research.registry;

import com.ikunkk02.wishingwillow.research.RegistryEntryType;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegistrySnapshotTest {
    @Test
    void attributesNamespacesAndLimitsRepresentativeSamples() {
        EnumMap<RegistryEntryType, List<String>> entries = new EnumMap<>(RegistryEntryType.class);
        entries.put(RegistryEntryType.ENTITY, ids("horror", 50));
        entries.put(RegistryEntryType.ITEM, ids("horror", 100));
        RegistrySnapshot snapshot = new RegistrySnapshot(entries, Map.of("horror", "horror_mod"), Set.of());

        assertEquals(50, snapshot.countsForMod("horror_mod").get(RegistryEntryType.ENTITY));
        assertEquals(32, snapshot.representativeEntries("horror_mod").get(RegistryEntryType.ENTITY).size());
        assertEquals(24, snapshot.representativeEntries("horror_mod").get(RegistryEntryType.ITEM).size());
        assertTrue(snapshot.entries().get(RegistryEntryType.ITEM).size() >
                snapshot.representativeEntries("horror_mod").get(RegistryEntryType.ITEM).size());
    }

    @Test
    void ambiguousNamespaceIsNeverAttributed() {
        RegistrySnapshot snapshot = new RegistrySnapshot(
                Map.of(RegistryEntryType.ENTITY, List.of("shared:watcher")),
                Map.of(), Set.of("shared"));
        assertEquals(0, snapshot.countsForMod("first").get(RegistryEntryType.ENTITY));
        assertEquals(0, snapshot.countsForMod("second").get(RegistryEntryType.ENTITY));
    }

    private static List<String> ids(String namespace, int count) {
        return IntStream.range(0, count).mapToObj(index -> namespace + ":entry_" + index).toList();
    }
}
