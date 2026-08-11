package com.ikunkk02.wishingwillow.program;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The NEW compiler only expands flow into flat {@link ProgramAction} leaves. It never lowers
 * into {@code WishPlanDraft}/{@code WishPlanStep} and never invokes the legacy planning stack.
 */
class WishProgramCompilerTest {
    private static final WishProgramCompiler COMPILER = new WishProgramCompiler();

    @Test
    void beneficialEffectGroupCompilesToOneCoreActionWithoutPresentationOrReviewer() {
        WishProgram program = WishProgramJson.parseAndValidate("""
                {"schema_version":1,"goal":"all beneficial effects","core_actions":[
                 {"action":"apply_effect_group","parameters":{"group":"beneficial","duration_seconds":600}}],
                 "presentation_actions":[],"skill":"","unknown_capability":""}
                """);
        CompiledWishProgram compiled = COMPILER.compile(program);
        assertEquals(1, compiled.coreActions().size());
        ProgramAction leaf = compiled.coreActions().get(0);
        assertEquals("apply_effect_group", leaf.actionId());
        assertEquals(0, leaf.group());
        assertEquals(0, leaf.stepIndex());
        assertFalse(leaf.presentation());
        assertTrue(compiled.presentationActions().isEmpty());
        assertEquals(1, compiled.leafCount());
        assertFalse(compiled.agentUsed());
    }

    @Test
    void fallingBlockProgramCompilesExactCountToPhysicalPrimitive() {
        WishProgram program = WishProgramJson.parseAndValidate("""
                {"schema_version":1,"goal":"100 diamond blocks fall from the sky","core_actions":[
                 {"action":"spawn_falling_block","parameters":{"block":"diamond_block","target":"self",
                  "height":30,"horizontal_radius":10,"count":100,"interval_ticks":2,"landing":"place_or_drop"}}],
                 "presentation_actions":[],"skill":"block_rain","unknown_capability":""}
                """);
        CompiledWishProgram compiled = COMPILER.compile(program);
        ProgramAction leaf = compiled.coreActions().get(0);
        assertEquals("spawn_falling_block", leaf.actionId());
        assertEquals(100, leaf.parameters().get("count").getAsInt());
        assertTrue(compiled.skillUsed());
        assertFalse(compiled.agentUsed());
    }

    @Test
    void celebratoryLightningIsOptionalPresentationAndIsNotSynthesized() {
        WishProgram program = WishProgramJson.parseAndValidate("""
                {"schema_version":1,"goal":"give 64 diamonds and strike lightning to celebrate","core_actions":[
                 {"action":"give_item","parameters":{"item":"minecraft:diamond","count":64}}],
                 "presentation_actions":[{"action":"spawn_lightning","parameters":{}}],
                 "skill":"","unknown_capability":""}
                """);
        CompiledWishProgram compiled = COMPILER.compile(program);
        assertEquals(List.of("give_item"), compiled.coreActions().stream().map(ProgramAction::actionId).toList());
        assertEquals(List.of("spawn_lightning"),
                compiled.presentationActions().stream().map(ProgramAction::actionId).toList());
        assertEquals(1, compiled.presentationActions().get(0).stepIndex());
        assertTrue(compiled.presentationActions().get(0).presentation());
    }

    @Test
    void parallelChildrenShareAGroupAndDelayProducesDelayedNextGroup() {
        WishProgram parallel = WishProgramJson.parseAndValidate("""
                {"schema_version":1,"goal":"reward with simultaneous celebration","core_actions":[
                 {"action":"give_item","parameters":{"item":"minecraft:diamond","count":64}}],
                 "presentation_actions":[{"action":"parallel","parameters":{"actions":[
                  {"action":"spawn_lightning","parameters":{}},
                  {"action":"play_sound","parameters":{"sound":"minecraft:entity.player.levelup"}}]}}],
                 "skill":"","unknown_capability":""}
                """);
        CompiledWishProgram parallelCompiled = COMPILER.compile(parallel);
        assertEquals(2, parallelCompiled.presentationActions().size());
        assertEquals(parallelCompiled.presentationActions().get(0).group(),
                parallelCompiled.presentationActions().get(1).group());

        WishProgram delayed = WishProgramJson.parseAndValidate("""
                {"schema_version":1,"goal":"reward then delayed celebration","core_actions":[
                 {"action":"give_item","parameters":{"item":"minecraft:diamond","count":64}}],
                 "presentation_actions":[{"action":"sequence","parameters":{"actions":[
                  {"action":"delay","parameters":{"ticks":40}},
                  {"action":"spawn_lightning","parameters":{}}]}}],
                 "skill":"","unknown_capability":""}
                """);
        ProgramAction delayedLeaf = COMPILER.compile(delayed).presentationActions().get(0);
        assertEquals(40, delayedLeaf.delayTicks());
    }

    @Test
    void sequenceLeavesGetDistinctIncreasingGroups() {
        WishProgram program = WishProgramJson.parseAndValidate("""
                {"schema_version":1,"goal":"weather first then time","core_actions":[
                 {"action":"set_weather","parameters":{"weather":"thunder"}},
                 {"action":"set_time","parameters":{"value":"night"}}],
                 "presentation_actions":[],"skill":"","unknown_capability":""}
                """);
        CompiledWishProgram compiled = COMPILER.compile(program);
        assertEquals(2, compiled.coreActions().size());
        assertTrue(compiled.coreActions().get(0).group() < compiled.coreActions().get(1).group());
        assertEquals(0, compiled.coreActions().get(0).stepIndex());
        assertEquals(1, compiled.coreActions().get(1).stepIndex());
    }

    @Test
    void programRejectsCommandsAndUnboundedRepeat() {
        assertThrows(IllegalArgumentException.class, () -> parse("unsafe", "repeat",
                "{\"count\":17,\"actions\":[{\"action\":\"play_sound\",\"parameters\":{\"sound\":\"minecraft:block.note_block.bell\"}}]}", "", ""));
        assertThrows(IllegalArgumentException.class, () -> parse("unsafe", "give_item",
                "{\"item\":\"/give @s diamond\",\"count\":1}", "", ""));
    }

    @Test
    void unknownCapabilityIsRejectedByTheNativeCompiler() {
        WishProgram program = new WishProgram(1, "use the mod's original tracking AI",
                List.of(), List.of(), "", "mod_specific_entity_ai");
        assertThrows(IllegalArgumentException.class, () -> COMPILER.compile(program));
    }

    private static WishProgram parse(String goal, String action, String parameters, String skill, String unknown) {
        return WishProgramJson.parseAndValidate("""
                {"schema_version":1,"goal":"%s","core_actions":[{"action":"%s","parameters":%s}],
                 "presentation_actions":[],"skill":"%s","unknown_capability":"%s"}
                """.formatted(goal, action, parameters, skill, unknown));
    }
}
