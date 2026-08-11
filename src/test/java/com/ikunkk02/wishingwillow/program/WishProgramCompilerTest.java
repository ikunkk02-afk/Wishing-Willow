package com.ikunkk02.wishingwillow.program;

import com.ikunkk02.wishingwillow.ai.*;
import com.ikunkk02.wishingwillow.contract.*;
import com.ikunkk02.wishingwillow.execution.ExecutionSettingsSnapshot;
import com.ikunkk02.wishingwillow.planning.*;
import com.ikunkk02.wishingwillow.research.RegistryEntryType;
import com.ikunkk02.wishingwillow.research.registry.RegistrySnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class WishProgramCompilerTest {
    @Test
    void beneficialEffectGroupCompilesToOneCoreActionWithoutPresentationOrReviewer() {
        WishProgram program = WishProgramJson.parseAndValidate("""
                {"schema_version":1,"goal":"all beneficial effects","core_actions":[
                 {"action":"apply_effect_group","parameters":{"group":"beneficial","duration_seconds":600}}],
                 "presentation_actions":[],"skill":"","unknown_capability":""}
                """);
        CompiledWishProgram compiled = compile(program, allPositiveEffectsWish());
        assertEquals(1, compiled.draft().steps().size());
        assertEquals(WishActionType.APPLY_EFFECT_CATEGORY, compiled.draft().steps().get(0).action());
        assertEquals("BENEFICIAL", compiled.draft().steps().get(0).parameters().get("category").getAsString());
        assertTrue(compiled.draft().steps().get(0).batchId().startsWith("wp:core"));
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
        CompiledWishProgram compiled = compile(program, fallingResource());
        var step = compiled.draft().steps().get(0);
        assertEquals(WishActionType.FALLING_BLOCK_SHOWER, step.action());
        assertEquals(100, step.parameters().get("count").getAsInt());
        assertEquals("minecraft:diamond_block", step.candidateReference().registryResource().id());
        assertTrue(compiled.skillUsed());
    }

    @Test
    void celebratoryLightningIsOptionalPresentationAndIsNotSynthesized() {
        WishProgram program = WishProgramJson.parseAndValidate("""
                {"schema_version":1,"goal":"give 64 diamonds and strike lightning to celebrate","core_actions":[
                 {"action":"give_item","parameters":{"item":"minecraft:diamond","count":64}}],
                 "presentation_actions":[{"action":"spawn_lightning","parameters":{}}],
                 "skill":"","unknown_capability":""}
                """);
        CompiledWishProgram compiled = compile(program, resourceWish());
        assertEquals(List.of(WishActionType.GIVE_ITEM, WishActionType.LIGHTNING),
                compiled.draft().steps().stream().map(WishPlanStep::action).toList());
        assertTrue(compiled.draft().steps().get(1).batchId().startsWith("wp:presentation"));
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
        CompiledWishProgram parallelCompiled = compile(parallel, resourceWish());
        assertEquals(parallelCompiled.draft().steps().get(1).batchId(),
                parallelCompiled.draft().steps().get(2).batchId());

        WishProgram delayed = WishProgramJson.parseAndValidate("""
                {"schema_version":1,"goal":"reward then delayed celebration","core_actions":[
                 {"action":"give_item","parameters":{"item":"minecraft:diamond","count":64}}],
                 "presentation_actions":[{"action":"sequence","parameters":{"actions":[
                  {"action":"delay","parameters":{"ticks":40}},
                  {"action":"spawn_lightning","parameters":{}}]}}],
                 "skill":"","unknown_capability":""}
                """);
        var delayedStep = compile(delayed, resourceWish()).draft().steps().get(1);
        assertEquals(WishStepTiming.DELAYED, delayedStep.timing());
        assertEquals(2, delayedStep.delaySeconds());
    }

    private static CompiledWishProgram compile(WishProgram program, WishInterpretation interpretation) {
        RegistrySnapshot registry = registry();
        CapabilityCatalog catalog = CapabilityCatalog.create(List.of(), List.of(), "READY", "", registry.digest());
        return new WishProgramCompiler().compile(program, interpretation, catalog, registry,
                ExecutionSettingsSnapshot.permissive());
    }

    private static WishInterpretation resourceWish() {
        WishContract contract = new WishContract(WishContractType.OBTAIN_RESOURCE, "Player obtains 64 diamonds", List.of(
                constraint(WishConstraintKind.RESOURCE_SEMANTIC, WishConstraintOperator.EQUALS, "diamond", 0),
                constraint(WishConstraintKind.MINIMUM_QUANTITY, WishConstraintOperator.AT_LEAST, "", 64),
                constraint(WishConstraintKind.REAL_RESOURCE, WishConstraintOperator.REQUIRED, "", 0),
                constraint(WishConstraintKind.PLAYER_ACCESSIBLE, WishConstraintOperator.REQUIRED, "", 0)));
        return interpretation(contract, List.of(WishCapability.GIVE_ITEM));
    }

    private static WishInterpretation fallingResource() {
        WishContract contract = new WishContract(WishContractType.OBTAIN_RESOURCE,
                "Player receives 100 diamond blocks falling from the sky", List.of(
                constraint(WishConstraintKind.RESOURCE_SEMANTIC, WishConstraintOperator.EQUALS, "diamond_block", 0),
                constraint(WishConstraintKind.MINIMUM_QUANTITY, WishConstraintOperator.AT_LEAST, "", 100),
                constraint(WishConstraintKind.REAL_RESOURCE, WishConstraintOperator.REQUIRED, "", 0),
                constraint(WishConstraintKind.PLAYER_ACCESSIBLE, WishConstraintOperator.REQUIRED, "", 0),
                constraint(WishConstraintKind.DELIVERY_SEMANTIC, WishConstraintOperator.EQUALS, "fall_from_sky", 0)));
        return interpretation(contract, List.of(WishCapability.BLOCK_CHANGE));
    }

    private static WishInterpretation allPositiveEffectsWish() {
        WishContract contract = new WishContract(WishContractType.CHANGE_PLAYER_STATE,
                "Player has every beneficial effect", List.of(
                constraint(WishConstraintKind.STATE_METRIC, WishConstraintOperator.EQUALS,
                        "all_positive_status_effects", 0),
                constraint(WishConstraintKind.TARGET_SCOPE, WishConstraintOperator.EQUALS, "player", 0)));
        return interpretation(contract, List.of(WishCapability.POWER_BUFF));
    }

    private static WishHardConstraint constraint(WishConstraintKind kind, WishConstraintOperator operator,
                                                 String semantic, int quantity) {
        return new WishHardConstraint(kind, operator, semantic, quantity, 0, true);
    }

    private static WishInterpretation interpretation(WishContract contract, List<WishCapability> capabilities) {
        return new WishInterpretation(2, "wish_program_test", contract.requiredOutcome(), contract,
                new WishFulfillment(WishFulfillmentMode.ABSURD, "Execute registered actions",
                        List.of(FulfillmentStyle.PHYSICAL_ABSURDITY), 80), "Program test",
                WishTone.ABSURD, 100, WishDelivery.IMMEDIATE, capabilities);
    }

    private static RegistrySnapshot registry() {
        return new RegistrySnapshot(Map.of(
                RegistryEntryType.ITEM, List.of("minecraft:diamond"),
                RegistryEntryType.BLOCK, List.of("minecraft:diamond_block"),
                RegistryEntryType.EFFECT, List.of("minecraft:speed"),
                RegistryEntryType.SOUND, List.of("minecraft:entity.player.levelup")),
                Map.of("minecraft", "minecraft"), Set.of());
    }
}
