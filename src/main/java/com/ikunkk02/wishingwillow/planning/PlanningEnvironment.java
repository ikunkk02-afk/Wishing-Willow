package com.ikunkk02.wishingwillow.planning;

import com.ikunkk02.wishingwillow.research.RegistryEntryType;

import java.util.Set;

public interface PlanningEnvironment {
    boolean contains(RegistryEntryType type, String id);
    boolean modLoaded(String modId, String version);
    default boolean modPresent(String modId, String storedVersion) { return modLoaded(modId, storedVersion); }
    default Set<String> beneficialStatusEffectIds() { return Set.of(); }
}
