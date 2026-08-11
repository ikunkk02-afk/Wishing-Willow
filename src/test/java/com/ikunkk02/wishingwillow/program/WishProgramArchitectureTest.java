package com.ikunkk02.wishingwillow.program;

import com.ikunkk02.wishingwillow.execution.WishActionLoopDetector;
import com.ikunkk02.wishingwillow.execution.WishExecutionState;
import com.ikunkk02.wishingwillow.execution.WishProgramResultPolicy;
import com.ikunkk02.wishingwillow.execution.WishStepExecutionState;
import com.ikunkk02.wishingwillow.execution.action.ActionResult;
import com.ikunkk02.wishingwillow.execution.action.ActionStatus;
import com.ikunkk02.wishingwillow.planning.WishActionRouter;
import com.ikunkk02.wishingwillow.planning.WishExecutionRoute;
import com.ikunkk02.wishingwillow.program.skill.WishSkillRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class WishProgramArchitectureTest {
    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("knownWishPrograms")
    void knownWishesChoosePrimitiveWithoutAgent(String goal, String action, String parameters) {
        WishProgram program = parse(goal, action, parameters, "", "");
        assertEquals(action, program.coreActions().get(0).action());
        assertFalse(program.requiresAgent());
        assertEquals(WishExecutionRoute.DIRECT_ACTION, new WishActionRouter().select(program).route());
    }

    static Stream<Arguments> knownWishPrograms() {
        return Stream.of(
                Arguments.of("give 64 diamonds", "give_item", "{\"item\":\"minecraft:diamond\",\"count\":64}"),
                Arguments.of("all beneficial effects", "apply_effect_group", "{\"group\":\"beneficial\",\"duration_seconds\":600}"),
                Arguments.of("speed five for ten minutes", "apply_effect", "{\"effect\":\"minecraft:speed\",\"duration_seconds\":600,\"amplifier\":4}"),
                Arguments.of("100 diamond blocks from the sky", "spawn_falling_block", "{\"block\":\"minecraft:diamond_block\",\"target\":\"self\",\"height\":30,\"horizontal_radius\":10,\"count\":100,\"interval_ticks\":2,\"landing\":\"place_or_drop\"}"),
                Arguments.of("gold block rain", "spawn_falling_block", "{\"block\":\"minecraft:gold_block\",\"count\":32}"),
                Arguments.of("summon ten chickens", "spawn_entity", "{\"entity\":\"minecraft:chicken\",\"count\":10}"),
                Arguments.of("thunder weather", "set_weather", "{\"weather\":\"thunder\"}"),
                Arguments.of("midnight", "set_time", "{\"value\":\"midnight\"}")
        );
    }

    @Test
    void knownSkillComposesActionsWithoutAgent() {
        WishProgram program = parse("block rain", "spawn_falling_block",
                "{\"block\":\"minecraft:sand\",\"count\":16}", "block_rain", "");
        WishSkillRegistry.defaults().validateSelection(program);
        assertTrue(program.usesSkill());
        assertFalse(program.requiresAgent());
    }

    @Test
    void unknownModCapabilityIsTheOnlyAgentRoute() {
        WishProgram program = new WishProgram(1, "use the mod's original tracking AI",
                List.of(), List.of(), "", "mod_specific_entity_ai");
        assertTrue(program.requiresAgent());
        assertEquals(WishExecutionRoute.COMPLEX_AGENT, new WishActionRouter().select(program).route());
    }

    @Test
    void identicalActionLoopStopsAfterTwoAttempts() {
        WishActionLoopDetector detector = new WishActionLoopDetector();
        assertTrue(detector.allow("spawn_entity|chicken"));
        assertTrue(detector.allow("spawn_entity|chicken"));
        assertFalse(detector.allow("spawn_entity|chicken"));
    }

    @Test
    void timeoutIsAnExplicitActionResult() {
        ActionResult result = ActionResult.timeout(100, 83);
        assertEquals(ActionStatus.TIMEOUT, result.status());
        assertEquals(17, result.failed());
    }

    @Test
    void newWishSupersedeStateIsTerminalAndNewProgramCanRun() {
        assertTrue(WishExecutionState.SUPERSEDED.terminal());
        assertFalse(WishExecutionState.RUNNING.terminal());
    }

    @Test
    void presentationFailureDoesNotOverturnSuccessfulCore() {
        assertEquals(WishExecutionState.COMPLETED, WishProgramResultPolicy.reduce(
                List.of(WishStepExecutionState.SUCCEEDED), List.of(WishStepExecutionState.FAILED)));
    }

    @Test
    void coreFailureCannotDisplaySuccess() {
        assertEquals(WishExecutionState.FAILED, WishProgramResultPolicy.reduce(
                List.of(WishStepExecutionState.FAILED), List.of(WishStepExecutionState.SUCCEEDED)));
    }

    @Test
    void programRejectsCommandsAndUnboundedRepeat() {
        assertThrows(IllegalArgumentException.class, () -> parse("unsafe", "repeat",
                "{\"count\":17,\"actions\":[{\"action\":\"play_sound\",\"parameters\":{\"sound\":\"minecraft:block.note_block.bell\"}}]}", "", ""));
        assertThrows(IllegalArgumentException.class, () -> parse("unsafe", "give_item",
                "{\"item\":\"/give @s diamond\",\"count\":1}", "", ""));
    }

    private static WishProgram parse(String goal, String action, String parameters, String skill, String unknown) {
        return WishProgramJson.parseAndValidate("""
                {"schema_version":1,"goal":"%s","core_actions":[{"action":"%s","parameters":%s}],
                 "presentation_actions":[],"skill":"%s","unknown_capability":"%s"}
                """.formatted(goal, action, parameters, skill, unknown));
    }
}
