package com.ikunkk02.wishingwillow.execution;

import com.ikunkk02.wishingwillow.execution.action.WishActionDefinition;
import com.ikunkk02.wishingwillow.execution.action.WishActionRegistry;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class WishActionDefinitionTest {
    @Test
    void registryContainsRequiredPrimitiveCatalog() {
        Set<String> required = Set.of("give_item", "remove_item", "apply_effect", "remove_effect",
                "clear_effects", "apply_effect_group", "modify_health", "modify_hunger",
                "modify_attribute", "teleport_player", "place_block", "replace_blocks",
                "place_pattern", "spawn_falling_block", "spawn_entity", "remove_entity",
                "set_entity_target", "follow_player", "avoid_player", "set_weather", "set_time",
                "spawn_lightning", "create_explosion", "play_sound", "spawn_particle",
                "repeat", "delay", "sequence", "parallel");
        assertTrue(WishActionRegistry.defaults().ids().containsAll(required));
    }

    @Test
    void fallingBlockDefinitionExplainsSelectionAndUsesRealExecutor() {
        WishActionDefinition action = WishActionRegistry.defaults().find("spawn_falling_block");
        assertNotNull(action);
        assertTrue(action.description().contains("USE WHEN:"));
        assertTrue(action.description().contains("fall from the sky"));
        assertTrue(action.description().contains("DO NOT USE WHEN:"));
        assertTrue(action.description().contains("RELATED ACTIONS:"));
        assertNotNull(action.executor());
        assertEquals(30, action.timeout().toSeconds());
    }

    @Test
    void catalogIsGeneratedFromDefinitions() {
        String catalog = WishActionRegistry.defaults().catalogPrompt();
        assertTrue(catalog.contains("spawn_falling_block"));
        assertTrue(catalog.contains("timeout_ms"));
        assertTrue(catalog.contains("parameter"));
    }
}
