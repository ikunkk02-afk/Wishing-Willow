package com.ikunkk02.wishingwillow.execution;
import org.junit.jupiter.api.Test;import java.util.UUID;import static org.junit.jupiter.api.Assertions.*;
class AttributeExecutorTest {@Test void modifierUuidIsStableAcrossReload(){UUID execution=UUID.randomUUID();UUID first=WishExecutionSafety.stableAttributeModifierId(execution,2,"MAX_HEALTH");assertEquals(first,WishExecutionSafety.stableAttributeModifierId(execution,2,"MAX_HEALTH"));assertNotEquals(first,WishExecutionSafety.stableAttributeModifierId(execution,3,"MAX_HEALTH"));}}
