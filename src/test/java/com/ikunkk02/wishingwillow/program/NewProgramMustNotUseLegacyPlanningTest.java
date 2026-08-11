package com.ikunkk02.wishingwillow.program;

import com.ikunkk02.wishingwillow.execution.WishPipelineProbe;
import com.ikunkk02.wishingwillow.planning.WishTargetType;
import com.ikunkk02.wishingwillow.research.RegistryEntryType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * HARD architectural guarantee: a normal Wish Program (give_item, apply_effect_group,
 * spawn_falling_block, spawn_entity, set_weather) must never touch the legacy planning stack.
 *
 * <p>Asserted via {@link WishPipelineProbe} counters: legacy plan lowering, contract
 * validation/review and agent runs must all stay at zero while the native compiler and
 * validator produce executable leaves with canonical parameters and resolved candidates.</p>
 */
class NewProgramMustNotUseLegacyPlanningTest {
    private static final WishProgramCompiler COMPILER = new WishProgramCompiler();
    private static final FakeResolver RESOLVER = new FakeResolver();

    @BeforeEach
    void resetProbes() {
        WishPipelineProbe.reset();
    }

    @Test
    void giveItemProgramNeverTouchesLegacyPlanning() {
        WishProgram program = program("give me 64 diamonds",
                "{\"action\":\"give_item\",\"parameters\":{\"item\":\"minecraft:diamond\",\"count\":64}}");
        CompiledWishProgram compiled = COMPILER.compile(program);
        ValidatedWishProgram validated = WishProgramValidator.validate(program, RESOLVER);

        assertEquals(1, compiled.coreActions().size());
        ProgramAction leaf = validated.coreActions().get(0);
        assertEquals("give_item", leaf.actionId());
        assertEquals(64, leaf.parameters().get("count").getAsInt());
        assertEquals("minecraft:diamond", leaf.candidate().registryResource().id());
        assertEquals(WishTargetType.PLAYER, leaf.target());

        assertLegacyCountersZero();
    }

    @Test
    void applyEffectGroupProgramNeverTouchesLegacyPlanning() {
        WishProgram program = program("all beneficial effects",
                "{\"action\":\"apply_effect_group\",\"parameters\":{\"group\":\"beneficial\",\"duration_seconds\":600}}");
        CompiledWishProgram compiled = COMPILER.compile(program);
        ValidatedWishProgram validated = WishProgramValidator.validate(program, RESOLVER);

        ProgramAction leaf = validated.coreActions().get(0);
        assertEquals("apply_effect_group", leaf.actionId());
        assertEquals("BENEFICIAL", leaf.parameters().get("category").getAsString());
        assertEquals(600, leaf.parameters().get("duration_seconds").getAsInt());
        assertEquals(WishTargetType.PLAYER, leaf.target());

        assertLegacyCountersZero();
    }

    @Test
    void fallingBlockProgramCanonicalizesAndResolvesWithoutLegacyPlanning() {
        WishProgram program = program("100 diamond blocks fall from the sky",
                "{\"action\":\"spawn_falling_block\",\"parameters\":{\"block\":\"diamond_block\",\"target\":\"self\","
                        + "\"height\":30,\"horizontal_radius\":10,\"count\":100,\"interval_ticks\":2,\"landing\":\"place_or_drop\"}}");
        CompiledWishProgram compiled = COMPILER.compile(program);
        ValidatedWishProgram validated = WishProgramValidator.validate(program, RESOLVER);

        assertEquals(1, compiled.coreActions().size());
        ProgramAction leaf = validated.coreActions().get(0);
        assertEquals("spawn_falling_block", leaf.actionId());
        assertEquals(100, leaf.parameters().get("count").getAsInt());
        assertEquals(30, leaf.parameters().get("spawn_height").getAsInt());
        assertEquals(10, leaf.parameters().get("radius").getAsInt());
        assertEquals(2, leaf.parameters().get("interval_ticks").getAsInt());
        assertEquals("PLACE_OR_DROP", leaf.parameters().get("landing_mode").getAsString());
        assertEquals("RANDOM", leaf.parameters().get("spread").getAsString());
        assertEquals("minecraft:diamond_block", leaf.candidate().registryResource().id());
        assertEquals(WishTargetType.PLAYER, leaf.target());

        assertLegacyCountersZero();
    }

    @Test
    void spawnEntityProgramNeverTouchesLegacyPlanning() {
        WishProgram program = program("summon ten chickens",
                "{\"action\":\"spawn_entity\",\"parameters\":{\"entity\":\"minecraft:chicken\",\"count\":10}}");
        CompiledWishProgram compiled = COMPILER.compile(program);
        ValidatedWishProgram validated = WishProgramValidator.validate(program, RESOLVER);

        ProgramAction leaf = validated.coreActions().get(0);
        assertEquals("spawn_entity", leaf.actionId());
        assertEquals(10, leaf.parameters().get("count").getAsInt());
        assertEquals("minecraft:chicken", leaf.candidate().registryResource().id());

        assertLegacyCountersZero();
    }

    @Test
    void setWeatherProgramNeverTouchesLegacyPlanning() {
        WishProgram program = program("change the weather to thunder",
                "{\"action\":\"set_weather\",\"parameters\":{\"weather\":\"thunder\",\"duration_seconds\":300}}");
        CompiledWishProgram compiled = COMPILER.compile(program);
        ValidatedWishProgram validated = WishProgramValidator.validate(program, RESOLVER);

        ProgramAction leaf = validated.coreActions().get(0);
        assertEquals("set_weather", leaf.actionId());
        assertEquals("THUNDER", leaf.parameters().get("weather").getAsString());
        assertEquals(WishTargetType.WORLD, leaf.target());

        assertLegacyCountersZero();
    }

    @Test
    void unknownRegistryResourceIsRejectedByTheNativeValidator() {
        WishProgram program = program("give me an unknown item",
                "{\"action\":\"give_item\",\"parameters\":{\"item\":\"evil:not_registered\",\"count\":1}}");
        assertThrows(IllegalArgumentException.class,
                () -> WishProgramValidator.validate(program, RESOLVER));
        assertLegacyCountersZero();
    }

    private static void assertLegacyCountersZero() {
        assertEquals(0, WishPipelineProbe.legacyPlanCompileCount(),
                "DirectActionPlanCompiler/WishProgramCompiler legacy lowering must not run");
        assertEquals(0, WishPipelineProbe.contractValidatorCount(),
                "WishContractValidator must not gate new programs");
        assertEquals(0, WishPipelineProbe.contractReviewerCount(),
                "WishContractReviewer must not run for new programs");
        assertEquals(0, WishPipelineProbe.agentRunCount(),
                "Complex Agent must not run for known primitives");
    }

    private static WishProgram program(String goal, String coreActionJson) {
        return WishProgramJson.parseAndValidate("""
                {"schema_version":1,"goal":"%s","core_actions":[%s],
                 "presentation_actions":[],"skill":"","unknown_capability":""}
                """.formatted(goal, coreActionJson));
    }

    private static final class FakeResolver implements WishProgramResourceResolver {
        private final Map<RegistryEntryType, Set<String>> entries = Map.of(
                RegistryEntryType.ITEM, Set.of("minecraft:diamond"),
                RegistryEntryType.BLOCK, Set.of("minecraft:diamond_block"),
                RegistryEntryType.ENTITY, Set.of("minecraft:chicken"),
                RegistryEntryType.EFFECT, Set.of("minecraft:speed"));

        @Override
        public String resolve(RegistryEntryType type, String id) {
            if (id == null || id.isBlank()) return null;
            String exact = id.contains(":") ? id : "minecraft:" + id;
            return entries.getOrDefault(type, Set.of()).contains(exact) ? exact : null;
        }

        @Override
        public String resolveDimension(String id) { return null; }

        @Override
        public boolean containsPredefinedEvent(String event) { return false; }
    }
}
