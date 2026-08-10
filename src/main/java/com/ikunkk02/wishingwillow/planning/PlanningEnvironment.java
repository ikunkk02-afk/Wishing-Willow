package com.ikunkk02.wishingwillow.planning;

import com.ikunkk02.wishingwillow.research.RegistryEntryType;

public interface PlanningEnvironment {
    boolean contains(RegistryEntryType type, String id);
    boolean modLoaded(String modId, String version);
    default boolean modPresent(String modId, String storedVersion) { return modLoaded(modId, storedVersion); }
}
