package com.ikunkk02.wishingwillow.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WishCapabilityNormalizerTest {
    @Test
    void normalizesEntityRemovalAliasesToOneCapability() {
        assertEquals(WishCapability.ENTITY_REMOVAL, WishCapabilityNormalizer.normalize("ENTITY_REMOVAL"));
        assertEquals(WishCapability.ENTITY_REMOVAL, WishCapabilityNormalizer.normalize("entity-removal"));
        assertEquals(WishCapability.ENTITY_REMOVAL, WishCapabilityNormalizer.normalize("remove_entity"));
        assertEquals(WishCapability.ENTITY_REMOVAL, WishCapabilityNormalizer.normalize("despawn_entity"));
        assertEquals(WishCapability.ENTITY_REMOVAL, WishCapabilityNormalizer.normalize("entity_suppression"));
        assertEquals(WishCapability.ENTITY_REMOVAL, WishCapabilityNormalizer.normalize("clear_entities"));
        assertEquals(WishCapability.ENTITY_REMOVAL, WishCapabilityNormalizer.normalize("remove_mobs"));
    }

    @Test
    void publishesAliasesForSchemaGeneration() {
        assertEquals(WishCapability.ENTITY_REMOVAL, WishCapabilityNormalizer.aliases().get("REMOVE_ENTITY"));
        assertTrue(WishCapabilityNormalizer.allowedValues().contains("ENTITY_REMOVAL"));
        assertTrue(WishCapabilityNormalizer.allowedValues().contains("ENTITY_SUPPRESSION"));
    }

    @Test
    void preservesExistingCanonicalCapabilities() {
        assertEquals(WishCapability.GIVE_ITEM, WishCapabilityNormalizer.normalize(" give_item "));
        assertEquals(WishCapability.SPAWN_ENTITY, WishCapabilityNormalizer.normalize("spawn-entity"));
    }

    @Test
    void rejectsUnknownOrMissingCapabilities() {
        assertThrows(IllegalArgumentException.class, () -> WishCapabilityNormalizer.normalize("erase_everything"));
        assertThrows(IllegalArgumentException.class, () -> WishCapabilityNormalizer.normalize(" "));
        assertThrows(IllegalArgumentException.class, () -> WishCapabilityNormalizer.normalize(null));
    }
}
