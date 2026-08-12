package com.ikunkk02.wishingwillow.execution;

import com.google.gson.JsonObject;
import com.ikunkk02.wishingwillow.ai.WishCapability;
import com.ikunkk02.wishingwillow.execution.action.WishActionDefinition;
import com.ikunkk02.wishingwillow.execution.action.WishActionRegistry;
import com.ikunkk02.wishingwillow.planning.WishActionType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntitySuppressionActionDefinitionTest {
    @Test
    void exposesPersistentEntitySuppressionAction() {
        WishActionDefinition action = WishActionRegistry.defaults().find("entity_suppression");

        assertNotNull(action);
        assertEquals(WishActionType.ENTITY_SUPPRESSION, action.legacyType());
        assertEquals(java.util.Set.of(WishCapability.ENTITY_REMOVAL, WishCapability.WORLD_EVENT),
                action.capabilities());
        assertNotNull(action.executor());
    }

    @Test
    void entitySuppressionSchemaIsStrictBoundedAndSafeByDefault() {
        JsonObject schema = WishActionRegistry.defaults().find("entity_suppression").parameterSchema();
        JsonObject properties = schema.getAsJsonObject("properties");

        assertFalse(schema.get("additionalProperties").getAsBoolean());
        assertTrue(schema.getAsJsonArray("required").isEmpty());
        assertEquals("all_mobs", properties.getAsJsonObject("group").get("default").getAsString());
        assertEquals("current_dimension", properties.getAsJsonObject("scope").get("default").getAsString());
        assertTrue(properties.getAsJsonObject("remove_existing").get("default").getAsBoolean());
        assertTrue(properties.getAsJsonObject("prevent_future").get("default").getAsBoolean());
        assertTrue(properties.getAsJsonObject("permanent").get("default").getAsBoolean());
        assertEquals("discard", properties.getAsJsonObject("disappearance_mode").get("default").getAsString());
        assertTrue(properties.getAsJsonObject("exclude_players").get("default").getAsBoolean());
        assertTrue(properties.getAsJsonObject("group").getAsJsonArray("enum").toString().contains("\"hostile\""));
        assertTrue(properties.getAsJsonObject("scope").getAsJsonArray("enum").toString().contains("\"all_dimensions\""));
    }

    @Test
    void keepsLegacyBoundedRemoveEntityApiAvailable() {
        WishActionDefinition legacy = WishActionRegistry.defaults().find("remove_entity");
        assertNotNull(legacy);
        assertEquals(WishActionType.DESPAWN_ENTITY, legacy.legacyType());
        assertEquals(WishCapability.ENTITY_REMOVAL, legacy.capabilities().iterator().next());
    }

    @Test
    void exposesBoundedEntitySpawningRestorationAction() {
        WishActionDefinition restore = WishActionRegistry.defaults().find("restore_entity_spawning");

        assertNotNull(restore);
        assertEquals(WishActionType.RESTORE_ENTITY_SPAWNING, restore.legacyType());
        JsonObject properties = restore.parameterSchema().getAsJsonObject("properties");
        assertEquals("all_mobs", properties.getAsJsonObject("group").get("default").getAsString());
        assertEquals("all_dimensions", properties.getAsJsonObject("scope").get("default").getAsString());
        assertEquals(12, properties.getAsJsonObject("initial_count").get("default").getAsInt());
        assertEquals(32, properties.getAsJsonObject("initial_count").get("maximum").getAsInt());
    }
}
