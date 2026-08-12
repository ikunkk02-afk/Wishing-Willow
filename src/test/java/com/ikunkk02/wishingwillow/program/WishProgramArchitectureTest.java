package com.ikunkk02.wishingwillow.program;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ikunkk02.wishingwillow.execution.WishActionLoopDetector;
import com.ikunkk02.wishingwillow.execution.WishExecutionState;
import com.ikunkk02.wishingwillow.execution.WishProgramResultPolicy;
import com.ikunkk02.wishingwillow.execution.WishExecutionOutcome;
import com.ikunkk02.wishingwillow.execution.WishStepExecutionState;
import com.ikunkk02.wishingwillow.execution.action.ActionResult;
import com.ikunkk02.wishingwillow.execution.action.ActionStatus;
import com.ikunkk02.wishingwillow.planning.WishActionRouter;
import com.ikunkk02.wishingwillow.planning.WishExecutionRoute;
import com.ikunkk02.wishingwillow.program.skill.WishSkillRegistry;
import com.ikunkk02.wishingwillow.program.skill.ActionRequirementGroup;
import com.ikunkk02.wishingwillow.program.skill.RequirementMode;
import com.ikunkk02.wishingwillow.program.skill.WishSkillDefinition;
import com.ikunkk02.wishingwillow.program.skill.WishSkillType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Set;
import java.time.Duration;
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
                Arguments.of("64 diamonds fall from the sky as items", "spawn_item_rain", "{\"item\":\"minecraft:diamond\",\"count\":64,\"target\":\"self\"}"),
                Arguments.of("100 apples rain from the sky", "spawn_item_rain", "{\"item\":\"minecraft:apple\",\"count\":100,\"target\":\"self\"}"),
                Arguments.of("all beneficial effects", "apply_effect_group", "{\"group\":\"beneficial\",\"duration_seconds\":600}"),
                Arguments.of("speed five for ten minutes", "apply_effect", "{\"effect\":\"minecraft:speed\",\"duration_seconds\":600,\"amplifier\":4}"),
                Arguments.of("100 diamond blocks from the sky", "spawn_falling_block", "{\"block\":\"minecraft:diamond_block\",\"target\":\"self\",\"height\":30,\"horizontal_radius\":10,\"count\":100,\"interval_ticks\":2,\"landing\":\"place_or_drop\"}"),
                Arguments.of("100 sand blocks fall from above", "spawn_falling_block", "{\"block\":\"minecraft:sand\",\"count\":100,\"target\":\"self\"}"),
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
    void strategySkillAcceptsAttractionAuraWithoutEveryRecommendedAction() {
        WishProgram program = program("never alone", "absurd_wish_realization",
                List.of(action("entity_attraction_aura")), List.of());

        assertDoesNotThrow(() -> WishSkillRegistry.defaults().validateSelection(program));
    }

    @Test
    void strategySkillAcceptsSpawnedPermanentFriendWithoutAttractionAura() {
        WishProgram program = program("a friend forever", "absurd_wish_realization",
                List.of(action("spawn_entity"), action("follow_player")), List.of());

        assertDoesNotThrow(() -> WishSkillRegistry.defaults().validateSelection(program));
    }

    @Test
    void recipeSkillRejectsProgramMissingItsRequiredAction() {
        WishProgram program = program("block rain", "block_rain",
                List.of(action("play_sound")), List.of());

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> WishSkillRegistry.defaults().validateSelection(program));
        assertEquals("SKILL_REQUIRED_ACTIONS_MISSING", error.getMessage());
    }

    @Test
    void dramaticItemRewardAcceptsCoreRewardWithoutEveryPresentationAction() {
        WishProgram program = program("dramatic reward", "dramatic_item_reward",
                List.of(action("give_item")), List.of(action("play_sound")));

        assertDoesNotThrow(() -> WishSkillRegistry.defaults().validateSelection(program));
    }

    @Test
    void dramaticItemRewardRejectsPresentationWithoutCoreReward() {
        WishProgram program = program("dramatic reward", "dramatic_item_reward",
                List.of(), List.of(action("play_sound"), action("spawn_particle")));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> WishSkillRegistry.defaults().validateSelection(program));
        assertEquals("SKILL_REQUIRED_ACTIONS_MISSING", error.getMessage());
    }

    @Test
    void unknownSkillRemainsInvalid() {
        WishProgram program = program("unknown", "not_registered",
                List.of(action("play_sound")), List.of());

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> WishSkillRegistry.defaults().validateSelection(program));
        assertEquals("UNKNOWN_SKILL", error.getMessage());
    }

    @Test
    void anyOfRequirementGroupAcceptsOneAlternative() {
        WishSkillRegistry registry = registryWithGroup(RequirementMode.ANY_OF, Set.of("give_item", "spawn_entity"));
        WishProgram program = program("one alternative", "group_skill",
                List.of(action("spawn_entity")), List.of());

        assertDoesNotThrow(() -> registry.validateSelection(program));
    }

    @Test
    void allOfRequirementGroupRejectsPartialAlternative() {
        WishSkillRegistry registry = registryWithGroup(RequirementMode.ALL_OF, Set.of("spawn_entity", "follow_player"));
        WishProgram program = program("partial recipe", "group_skill",
                List.of(action("spawn_entity")), List.of());

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> registry.validateSelection(program));
        assertEquals("SKILL_REQUIRED_ACTIONS_MISSING", error.getMessage());
    }

    @Test
    void candidatePromptExplainsStrategyRecommendationsAndGroups() {
        JsonObject candidate = JsonParser.parseString(
                        WishSkillRegistry.defaults().candidatePrompt("I wish I would never be lonely"))
                .getAsJsonArray().get(0).getAsJsonObject();

        assertEquals("absurd_wish_realization", candidate.get("id").getAsString());
        assertEquals("strategy", candidate.get("skill_type").getAsString());
        assertTrue(candidate.getAsJsonArray("required_actions").isEmpty());
        assertTrue(candidate.getAsJsonArray("recommended_actions").asList().stream()
                .anyMatch(value -> value.getAsString().equals("entity_attraction_aura")));
        assertTrue(candidate.has("requirement_groups"));
    }

    @Test
    void advancedItemSkillRequiresRealItemStackRealizationAndForbidsBuffSubstitution() {
        JsonObject candidate = JsonParser.parseString(
                        WishSkillRegistry.defaults().candidatePrompt("我想要一把顶级附魔的钻石剑"))
                .getAsJsonArray().get(0).getAsJsonObject();

        assertEquals("advanced_item_realization", candidate.get("id").getAsString());
        assertTrue(candidate.getAsJsonArray("required_actions").toString().contains("give_item"));
        String template = candidate.get("parameter_template").getAsString();
        assertTrue(template.contains("ItemStack"));
        assertTrue(template.contains("Do not substitute player potion effects"));
        assertTrue(template.contains("MAXED"));
        assertTrue(template.contains("allow_incompatible_enchantments"));
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
        assertEquals(WishExecutionState.UNEXECUTABLE, WishProgramResultPolicy.reduce(
                List.of(WishStepExecutionState.FAILED), List.of(WishStepExecutionState.SUCCEEDED)));
    }

    @Test
    void mixedCoreResultsArePartialSuccessAndPresentationIsCountedSeparately() {
        WishProgramResultPolicy.Summary summary = WishProgramResultPolicy.summarize(
                List.of(WishStepExecutionState.SUCCEEDED, WishStepExecutionState.FAILED),
                List.of(WishStepExecutionState.SUCCEEDED, WishStepExecutionState.FAILED));
        assertEquals(WishExecutionOutcome.PARTIAL_SUCCESS, summary.outcome());
        assertEquals(1, summary.coreSuccess());
        assertEquals(1, summary.coreFailed());
        assertEquals(1, summary.presentationSuccess());
        assertEquals(1, summary.presentationFailed());
    }

    @Test
    void allHardLimitedCoreFailuresAreUnexecutable() {
        assertEquals(WishExecutionOutcome.UNEXECUTABLE, WishProgramResultPolicy.summarize(
                List.of(WishStepExecutionState.FAILED), List.of()).outcome());
    }

    @Test
    void runtimeExceptionFailureRemainsFailed() {
        assertEquals(WishExecutionOutcome.FAILED, WishProgramResultPolicy.outcomeForFailure("ACTION_EXCEPTION_NPE"));
    }

    @Test
    void internalCoreFailureProducesFailedRatherThanUnexecutable() {
        var record = new com.ikunkk02.wishingwillow.execution.WishExecutionRecord(
                java.util.UUID.randomUUID(), java.util.UUID.randomUUID(), java.util.UUID.randomUUID(),
                java.util.UUID.randomUUID(), 1, 0,
                com.ikunkk02.wishingwillow.execution.ExecutionSource.WISH_PROGRAM, 1);
        record.step(0).result(com.ikunkk02.wishingwillow.execution.WishActionResult.failed("ACTION_EXCEPTION_NPE"));
        record.step(0).transition(WishStepExecutionState.FAILED, 0);
        assertEquals(WishExecutionState.FAILED, WishProgramResultPolicy.reduceSteps(record.steps(), 1));
    }

    @Test
    void presentationOnlyProgramCannotPretendToSucceed() {
        assertEquals(WishExecutionOutcome.UNEXECUTABLE, WishProgramResultPolicy.summarize(
                List.of(), List.of(WishStepExecutionState.SUCCEEDED)).outcome());
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

    private static WishProgram program(String goal, String skill, List<WishProgramAction> core,
                                       List<WishProgramAction> presentation) {
        return new WishProgram(WishProgram.CURRENT_SCHEMA_VERSION, goal, core, presentation, skill, "");
    }

    private static WishProgramAction action(String id) {
        return new WishProgramAction(id, new JsonObject());
    }

    private static WishSkillRegistry registryWithGroup(RequirementMode mode, Set<String> actions) {
        return new WishSkillRegistry(List.of(new WishSkillDefinition("group_skill", "test", Set.of("test"),
                WishSkillType.RECIPE, Set.of(), Set.of(),
                List.of(new ActionRequirementGroup(mode, actions)), "", List.of(), Duration.ofSeconds(1))));
    }
}
