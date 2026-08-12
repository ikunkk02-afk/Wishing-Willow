package com.ikunkk02.wishingwillow.program;

import com.ikunkk02.wishingwillow.research.RegistryEntryType;
import com.ikunkk02.wishingwillow.research.registry.RegistrySnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class WishProgramResourceKindValidatorTest {
    private static final RegistrySnapshot SNAPSHOT = new RegistrySnapshot(Map.of(
            RegistryEntryType.ITEM, List.of("minecraft:diamond", "minecraft:diamond_block"),
            RegistryEntryType.BLOCK, List.of("minecraft:diamond_block", "minecraft:sand")
    ), Map.of("minecraft", "minecraft"), Set.of());

    @Test
    void snapshotReportsItemSubmittedToBlockActionBeforeNetworkSubmission() {
        WishProgram program = program("spawn_falling_block", "block", "minecraft:diamond");
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> WishProgramResourceKindValidator.validate(program, SNAPSHOT));
        assertEquals("RESOURCE_KIND_MISMATCH:action=spawn_falling_block parameter=block "
                + "resource=minecraft:diamond expected=BLOCK actual=ITEM", error.getMessage());
    }

    @Test
    void blockItemExistingInBothRegistriesFollowsTheChosenActionSemantic() {
        assertDoesNotThrow(() -> WishProgramResourceKindValidator.validate(
                program("spawn_item_rain", "item", "minecraft:diamond_block"), SNAPSHOT));
        assertDoesNotThrow(() -> WishProgramResourceKindValidator.validate(
                program("spawn_falling_block", "block", "minecraft:diamond_block"), SNAPSHOT));
    }

    @Test
    void incompleteSnapshotDoesNotReplaceServerAuthority() {
        assertDoesNotThrow(() -> WishProgramResourceKindValidator.validate(
                program("spawn_item_rain", "item", "modded:unknown_item"), SNAPSHOT));
    }

    private static WishProgram program(String action, String parameter, String resource) {
        return WishProgramJson.parseAndValidate("""
                {"schema_version":1,"goal":"resource kind","core_actions":[
                 {"action":"%s","parameters":{"%s":"%s","count":1}}],
                 "presentation_actions":[],"skill":"","unknown_capability":""}
                """.formatted(action, parameter, resource));
    }
}