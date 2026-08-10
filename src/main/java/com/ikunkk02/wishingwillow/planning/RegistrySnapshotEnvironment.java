package com.ikunkk02.wishingwillow.planning;

import com.ikunkk02.wishingwillow.research.RegistryEntryType;
import com.ikunkk02.wishingwillow.research.registry.RegistrySnapshot;

public record RegistrySnapshotEnvironment(RegistrySnapshot snapshot) implements PlanningEnvironment {
    @Override public boolean contains(RegistryEntryType type, String id) { return snapshot.contains(type, id); }
    @Override public boolean modLoaded(String modId, String version) { return true; }
}
